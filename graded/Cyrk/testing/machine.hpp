#ifndef MACHINE_HPP
#define MACHINE_HPP

#include <exception>
#include <memory>
#include <thread>
#include <iostream>

template <typename T, typename V>
bool checkType(const V* v) {
    return dynamic_cast<const T*>(v) != nullptr;
}

template <typename T> std::string type_name();

class MachineFailure : public std::exception
{
};

class MachineNotWorking : public std::exception
{
};

class BadProductException : public std::exception
{
};


class Product
{
public:
    Product() = default;
    Product(const Product&) = delete;
    Product& operator=(const Product&) = delete;
    virtual ~Product() = default;
};

class Machine
{
public:
    virtual ~Machine() = default;
    virtual std::unique_ptr<Product> getProduct() = 0;
    virtual void returnProduct(std::unique_ptr<Product> product) = 0;
    virtual void start() = 0;
    virtual void stop() = 0;
    virtual void printProductName() = 0;
};

class Szarlotka : public Product
{
};

class Kremowka : public Product
{
};

class VerySlowMachine : public Machine
{
    size_t burgersMade;
    std::chrono::milliseconds time = std::chrono::milliseconds(1000);
public:
    VerySlowMachine() : burgersMade(0) {}

    std::unique_ptr<Product> getProduct()
    {   
        std::this_thread::sleep_for(time);
        return std::unique_ptr<Product>(new Szarlotka());
    }

    void returnProduct(std::unique_ptr<Product> product)
    {
        if (!checkType<Szarlotka>(product.get())) throw BadProductException();
        burgersMade++;
    }

    void start()
    {
        burgersMade = 0;
    }

    void stop() {}

    void printProductName() {
        std::cout << "Szarlotka" << std::endl;
    }
};

class SlowMachine : public Machine
{
    size_t burgersMade;
    std::chrono::milliseconds time = std::chrono::milliseconds(300);
public:
    SlowMachine() : burgersMade(0) {}

    std::unique_ptr<Product> getProduct()
    {   
        std::this_thread::sleep_for(time);
        return std::unique_ptr<Product>(new Szarlotka());
    }

    void returnProduct(std::unique_ptr<Product> product)
    {
        if (!checkType<Szarlotka>(product.get())) throw BadProductException();
        burgersMade++;
    }

    void start()
    {
        burgersMade = 0;
    }

    void stop() {}

    void printProductName() {
        std::cout << "Szarlotka" << std::endl;
    }
};


class FastMachine : public Machine
{
    size_t burgersMade;
    std::chrono::milliseconds time = std::chrono::milliseconds(50);
public:
    FastMachine() : burgersMade(0) {}

    std::unique_ptr<Product> getProduct()
    {   
        std::this_thread::sleep_for(time);
        return std::unique_ptr<Product>(new Szarlotka());
    }

    void returnProduct(std::unique_ptr<Product> product)
    {   
        if (!checkType<Szarlotka>(product.get())) throw BadProductException();
        burgersMade++;
    }

    void start() {
        burgersMade = 0;
    }

    void stop() {}

    void printProductName() {
        std::cout << "Szarlotka" << std::endl;
    }
};

#endif // MACHINE_HPP