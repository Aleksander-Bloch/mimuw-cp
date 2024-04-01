#ifndef SIMPLE_HPP
#define SIMPLE_HPP

#include <atomic>
#include <chrono>
#include <thread>
#include <iostream>
#include <assert.h>

#include "machine.hpp"
#include "system.hpp"

void simpleTest() {
    using namespace std::chrono_literals;
    std::vector<std::jthread> clients;
    std::vector<std::string> products = {"burger"};

    std::cout << "Simple test started.\n";
    machines_t machines = {
        {"burger", std::shared_ptr<Machine>(new SlowMachine())}
    };

    System system{std::move(machines), 13, 5};

    auto client = std::jthread([&system](){
        system.getMenu();

        auto p = system.order({"burger"});
        p->wait();

        std::vector<product_t> test = system.collectOrder(std::move(p));
        assert(test.size() == 1);
    });

    client.join();
    system.shutdown();
}

#endif // SIMPLE_HPP
