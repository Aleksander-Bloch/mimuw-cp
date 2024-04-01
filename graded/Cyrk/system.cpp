#include "system.hpp"

#include <utility>
#include <queue>

Order::Order(unsigned int orderId, std::vector<std::string> products, order_ready_t orderReady)
        : id(orderId), products(std::move(products)), orderReady(std::move(orderReady)) {}

CoasterPager::CoasterPager(unsigned int orderId, CoasterPager::buzzer_t buzzer)
        : orderId(orderId), buzzer(std::move(buzzer)) {}

void CoasterPager::wait() const {
    if (!isReady()) {
        buzzer.get();
    }
}

void CoasterPager::wait(unsigned int timeout) const {
    if (!isReady()) {
        buzzer.wait_for(std::chrono::milliseconds(timeout));
    }
}

unsigned int CoasterPager::getId() const {
    return orderId;
}

bool CoasterPager::isReady() const {
    if (buzzer.valid()) {
        auto status = buzzer.wait_for(std::chrono::milliseconds(0));
        return status == std::future_status::ready;
    }
    return true;
}

System::System(System::machines_t machines, unsigned int numberOfWorkers, unsigned int clientTimeout)
        : machines(std::move(machines)), numberOfWorkers(numberOfWorkers), clientTimeout(clientTimeout) {
    for (const auto &[product, machinePtr]: this->machines) {
        setMenu.insert(product);
        machineWrappers.emplace_back();
        machineWrappers.back().productMade = product;
        machinePtr->start();
    }
    workers.resize(numberOfWorkers);
    for (auto & worker : workers) {
        workerThreads.emplace_back([&worker, this]{ worker.work(*this); });
    }
    for (auto & machineWrapper: machineWrappers) {
        machineWrapperThreads.emplace_back([&machineWrapper, this] { machineWrapper.makeProducts(*this); });
    }

}

std::vector<WorkerReport> System::shutdown() {
    std::unique_lock<std::mutex> lock{systemMutex};
    isRestaurantClosed = true;
    setMenu.clear();
    freeWorkers.notify_all();
    for (auto &workerThread: workerThreads) {
        workerThread.join();
    }
    for (const auto &[product, machinePtr]: machines) {
        unusedMachines[product].notify_one();
    }
    for (auto &machineWrapperThread : machineWrapperThreads) {
        machineWrapperThread.join();
    }
    std::vector<WorkerReport> workerReports;
    for (auto &worker: workers) {
        workerReports.push_back(worker.workerReport);
    }
    for (const auto &[product, machinePtr]: machines) {
        machinePtr->stop();
    }
    return workerReports;
}

std::vector<std::string> System::getMenu() const {
    std::unique_lock<std::mutex> lock{systemMutex};
    return {setMenu.begin(), setMenu.end()};
}

std::vector<unsigned int> System::getPendingOrders() const {
    std::unique_lock<std::mutex> lock{systemMutex};
    return {pendingOrders.begin(), pendingOrders.end()};
}

unsigned int System::getClientTimeout() const {
    std::unique_lock<std::mutex> lock{systemMutex};
    return clientTimeout;
}

std::unique_ptr<CoasterPager> System::order(std::vector<std::string> products) {
    std::unique_lock<std::mutex> lock{systemMutex};
    if (isRestaurantClosed) {
        throw RestaurantClosedException();
    }

    for (const auto &product: products) {
        if (setMenu.find(product) == setMenu.end()) {
            throw BadOrderException();
        }
    }

    Order::order_ready_t orderReady;
    CoasterPager::buzzer_t buzzer = orderReady.get_future();
    unsigned int orderId = this->nextOrderId++;

    promisedOrders[orderId] = std::promise<std::vector<std::unique_ptr<Product>>>{};
    futureOrders[orderId] = promisedOrders[orderId].get_future();
    isWaitingForPickup[orderId] = false;

    Order order = Order(orderId, std::move(products), std::move(orderReady));
    unassignedOrders.push(std::move(order));
    pendingOrders.insert(orderId);
    freeWorkers.notify_one();

    return std::make_unique<CoasterPager>(CoasterPager(orderId, std::move(buzzer)));
}

std::vector<std::unique_ptr<Product>> System::collectOrder(std::unique_ptr<CoasterPager> CoasterPager) {
    std::unique_lock<std::mutex> lock(systemMutex);
    if (!futureOrders[CoasterPager->orderId].valid()) {
        throw BadPagerException();
    }
    if (!isWaitingForPickup[CoasterPager->orderId]) {
        throw OrderNotReadyException();
    }
    isWaitingForPickup[CoasterPager->orderId] = false;
    waitingForPickup[CoasterPager->orderId].notify_all();

    return futureOrders[CoasterPager->orderId].get();
}

void Worker::work(System &system) {
    std::mutex mutex;
    while (true) {
        if (system.isRestaurantClosed) {
            break;
        }

        std::unique_lock<std::mutex> lock{mutex};
        system.freeWorkers
                .wait(lock, [&system] {
                    return system.isRestaurantClosed || !system.unassignedOrders.empty();
                });

        if (system.isRestaurantClosed) {
            break;
        }

        Order order = std::move(system.unassignedOrders.front());
        system.unassignedOrders.pop();

        std::vector<std::future<std::unique_ptr<Product>>> futureProducts(order.products.size());
        std::vector<std::promise<std::unique_ptr<Product>>> promisedProducts(order.products.size());
        for (size_t i = 0; i < order.products.size(); i++) {
            futureProducts[i] = promisedProducts[i].get_future();
            system.machines_jobs[order.products[i]].emplace(order.id, std::make_shared<std::promise<std::unique_ptr<Product>>>(std::move(promisedProducts[i])));
            system.unusedMachines[order.products[i]].notify_one();
        }

        std::vector<std::unique_ptr<Product>> collectedProducts;
        std::vector<std::string> collectedProductsNames;

        for (size_t i = 0; i < order.products.size(); i++) {
            try {
                auto collectedProduct = futureProducts[i].get();
                collectedProducts.push_back(std::move(collectedProduct));
                collectedProductsNames.push_back(order.products[i]);
            } catch (const MachineFailure &e) {
                workerReport.failedProducts.push_back(order.products[i]);
            }
        }

        if (collectedProducts.size() != order.products.size()) {
            workerReport.failedOrders.push_back(order.products);
            system.pendingOrders.erase(order.id);
            for (size_t i = 0; i < collectedProducts.size(); i++) {
                system.machines[collectedProductsNames[i]]->returnProduct(std::move(collectedProducts[i]));
            }
            system.promisedOrders[order.id].set_exception(std::make_exception_ptr(FulfillmentFailure()));
            order.orderReady.set_value("MACHINE MALFUNCTION");
            system.isWaitingForPickup[order.id] = true;
            continue;
        }

        order.orderReady.set_value("READY");
        system.isWaitingForPickup[order.id] = true;
        bool isPickedUp = system.waitingForPickup[order.id].wait_for(
                lock, std::chrono::milliseconds(system.getClientTimeout()),
                [&system, &order]{ return !system.isWaitingForPickup[order.id]; }
                );

        if (!isPickedUp) {
            system.promisedOrders[order.id].set_exception(std::make_exception_ptr(OrderExpiredException()));
            workerReport.abandonedOrders.push_back(order.products);
            system.pendingOrders.erase(order.id);
            for (size_t i = 0; i < collectedProducts.size(); i++) {
                system.machines[collectedProductsNames[i]]->returnProduct(std::move(collectedProducts[i]));
            }
        } else {
            system.promisedOrders[order.id].set_value(std::move(collectedProducts));
            workerReport.collectedOrders.push_back(order.products);
        }
    }
}

void MachineWrapper::makeProducts(System &system) {
    std::mutex mutex;
    while (true) {
        if (system.isRestaurantClosed) {
            break;
        }

        std::unique_lock<std::mutex> lock(mutex);
        system.unusedMachines[productMade]
                .wait(lock, [&system, this] {
                    return system.isRestaurantClosed || !system.machines_jobs[productMade].empty();
                });

        if (system.isRestaurantClosed) {
            break;
        }

        try {
            auto product = system.machines[productMade]->getProduct();
            system.machines_jobs[productMade].top().second->set_value(std::move(product));
            system.machines_jobs[productMade].pop();
        } catch (const MachineFailure &e) {
            while (!system.machines_jobs[productMade].empty()) {
                system.machines_jobs[productMade].top().second->set_exception(std::make_exception_ptr(MachineFailure()));
                system.machines_jobs[productMade].pop();
            }
            system.setMenu.erase(productMade);
        }
    }
}
