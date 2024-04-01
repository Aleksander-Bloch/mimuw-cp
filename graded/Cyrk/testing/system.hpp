#ifndef SYSTEM_HPP
#define SYSTEM_HPP

#include <exception>
#include <vector>
#include <unordered_map>
#include <unordered_set>
#include <functional>
#include <future>
#include <queue>
#include <set>

#include "machine.hpp"

typedef std::unordered_map<std::string, std::shared_ptr<Machine>> machines_t;
typedef std::unique_ptr<Product> product_t;

class FulfillmentFailure : public std::exception {
};

class OrderNotReadyException : public std::exception {
};

class BadOrderException : public std::exception {
};

class BadPagerException : public std::exception {
};

class OrderExpiredException : public std::exception {
};

class RestaurantClosedException : public std::exception {
};

struct WorkerReport {
    std::vector<std::vector<std::string>> collectedOrders;
    std::vector<std::vector<std::string>> abandonedOrders;
    std::vector<std::vector<std::string>> failedOrders;
    std::vector<std::string> failedProducts;
};

typedef std::pair<unsigned int, std::shared_ptr<std::promise<std::unique_ptr<Product>>>> job_t;

class MachineJobCmp {
public:
    bool operator() (job_t &a, job_t &b) {
        return a.first > b.first;
    }
};

class Order {
public:
    typedef std::promise<std::string> order_ready_t;

    Order(unsigned int orderId, std::vector<std::string> products, order_ready_t orderReady);

    unsigned int id;
    std::vector<std::string> products;
    order_ready_t orderReady;
};

class CoasterPager {
public:
    typedef std::future<std::string> buzzer_t;

    CoasterPager(unsigned int orderId, buzzer_t buzzer);

    void wait() const;

    void wait(unsigned int timeout) const;

    [[nodiscard]] unsigned int getId() const;

    [[nodiscard]] bool isReady() const;

    unsigned int orderId;
    mutable buzzer_t buzzer;
};

class Worker;
class MachineWrapper;

class System {
public:
    typedef std::unordered_map<std::string, std::shared_ptr<Machine>> machines_t;
    typedef std::unordered_map<std::string, std::priority_queue<job_t, std::vector<job_t>, MachineJobCmp>> machines_jobs_t;
    typedef std::unordered_map<unsigned int, std::promise<std::vector<std::unique_ptr<Product>>>> promised_orders_t;
    typedef std::unordered_map<unsigned int, std::future<std::vector<std::unique_ptr<Product>>>> future_orders_t;
    typedef std::unordered_map<unsigned int, bool> wait_status_t;

    System(machines_t machines, unsigned int numberOfWorkers, unsigned int clientTimeout);

    std::vector<WorkerReport> shutdown();

    std::vector<std::string> getMenu() const;

    std::vector<unsigned int> getPendingOrders() const;

    std::unique_ptr<CoasterPager> order(std::vector<std::string> products);

    std::vector<std::unique_ptr<Product>> collectOrder(std::unique_ptr<CoasterPager> CoasterPager);

    unsigned int getClientTimeout() const;

    machines_t machines;
    machines_jobs_t machines_jobs;
    promised_orders_t promisedOrders;
    future_orders_t futureOrders;
    wait_status_t isWaitingForPickup;
    unsigned int numberOfWorkers;
    unsigned int clientTimeout;
    std::unordered_set<std::string> setMenu;
    std::unordered_set<unsigned int> pendingOrders;
    mutable std::mutex systemMutex;
    std::condition_variable freeWorkers;
    std::unordered_map<std::string, std::condition_variable> unusedMachines;
    std::unordered_map<unsigned int, std::condition_variable> waitingForPickup;
    std::queue<Order> unassignedOrders;
    unsigned int nextOrderId{0};
    bool isRestaurantClosed{false};
    std::vector<Worker> workers;
    std::vector<MachineWrapper> machineWrappers;
    std::vector<std::thread> workerThreads;
    std::vector<std::thread> machineWrapperThreads;
};

class Worker {
public:
    void work(System &system);

    struct WorkerReport workerReport;
};

class MachineWrapper {
public:
    void makeProducts(System &system);

    std::string productMade;
};

#endif // SYSTEM_HPP
