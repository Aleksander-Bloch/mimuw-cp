#ifndef EFFICIENCY_HPP
#define EFFICIENCY_HPP

#include <atomic>
#include <chrono>
#include <deque>
#include <thread>
#include <latch>
#include <iostream>
#include <assert.h>

#include "machine.hpp"
#include "system.hpp"

// TODO
std::vector<std::jthread> clients;
std::vector<std::string> products = {"burger", "chips", "icecream", "pizza", "cola", "popcorn", "fries", "salad", "sandwich", "soda", "tea", "water"};

std::vector<std::string> bigMeals = {"bigmac"};
std::vector<std::string> foods = {"burger", "chips", "icecream", "pizza", "popcorn", "fries", "salad", "sandwich"};
std::vector<std::string> drinks = {"cola", "soda", "tea", "water"};

void efficiencyTest() {
    using namespace std::chrono_literals;
    std::cout << "Hello World!\n";
    machines_t machines;
    for (auto& food : foods) {
        machines[food] = std::shared_ptr<Machine>(new SlowMachine());
    }

    for (auto& drink : drinks) {
        machines[drink] = std::shared_ptr<Machine>(new FastMachine());
    }

    for (auto& product : bigMeals) {
        machines[product] = std::shared_ptr<Machine>(new VerySlowMachine());
    }

    System system{std::move(machines), 13, 5};

    std::cout << "System created.\n";

    for (unsigned int i = 0; i < 10; i++) {
        auto client = std::jthread([i, &system](){
            system.getMenu();
            std::string orderProduct;
            orderProduct = products[i % products.size()];
            if (i % 30 == 0) {
                orderProduct = bigMeals[i % bigMeals.size()];
            }

            auto p = system.order({orderProduct});
            p->wait();

            std::vector<product_t> test = system.collectOrder(std::move(p));
            assert(test.size() == 1);
        });

        clients.push_back(std::move(client));
    }

    for (auto& client : clients) {
        client.join();
    }

    std::vector<WorkerReport> reports = system.shutdown();
//    unsigned int index = 0;
//    for (auto& report : reports) {
//        index++;
//        std::cout << "Worker " << index << " report: " << report.toString() << std::endl;
//    }
}

#endif // EFFICIENCY_HPP
