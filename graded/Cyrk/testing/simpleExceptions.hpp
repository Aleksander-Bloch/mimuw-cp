#ifndef SIMPLE_EXCEPTIONS_HPP
#define SIMPLE_EXCEPTIONS_HPP

// TODO - jak niby ma być zrobiony BadPagerException?

#include <iostream>
#include <chrono>
#include <thread>
#include <cassert>

#include "system.hpp"
#include "machine.hpp"

class PapieshMachine : public Machine
{
    size_t kremowkiMade;
    std::chrono::seconds time = std::chrono::seconds(10);

public:
    PapieshMachine() : kremowkiMade(0) {}

    std::unique_ptr<Product> getProduct()
    {
        std::this_thread::sleep_for(time);
        return std::unique_ptr<Product>(new Kremowka());
    }

    void returnProduct(std::unique_ptr<Product> product)
    {
        if (!checkType<Kremowka>(product.get())) throw BadProductException();
        kremowkiMade++;
    }

    void start()
    {
        kremowkiMade = 0;
    }

    void stop() {}

    void printProductName() {
        std::cout << "Kremowka" << std::endl;
    }
};

void simpleExceptionsTest() {
    using namespace std::chrono_literals;
    std::cout << "Excepitons test started\n";
    machines_t machines = {
        {"kremowka", std::shared_ptr<Machine>(new PapieshMachine())},
        {"szarlotka", std::shared_ptr<Machine>(new FastMachine())}
    };

    System system{std::move(machines), 2, 2};

    auto pingwinek = std::jthread([&system](){
        system.getMenu();

        auto p = system.order({"kremowka"});
        p->wait(1);
        std::vector<product_t> test;

        try {
            test = system.collectOrder(std::move(p));
        } catch (const OrderNotReadyException &e) {
            std::cout << "Exception caught: Order not ready." << std::endl;
        }

        assert(test.size() == 0);
    });

    auto koala = std::jthread([&system](){
        system.getMenu();

        auto p = system.order({"szarlotka"});
        p->wait();
        std::cout << "Order ready, waiting for 5 seconds." << std::endl;
        std::this_thread::sleep_for(5s);
        std::cout << "5 seconds passed." << std::endl;
        std::vector<product_t> test;

        try {
            test = system.collectOrder(std::move(p));
        } catch (const OrderExpiredException &e) {
            std::cout << "Exception caught: Order expired." << std::endl;
        }

        assert(test.size() == 0);
    });

    pingwinek.join();
    koala.join();

    system.shutdown();
}

#endif // SIMPLE_EXCEPTIONS_HPP
