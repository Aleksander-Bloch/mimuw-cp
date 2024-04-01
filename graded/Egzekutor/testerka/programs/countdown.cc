#include<bits/stdc++.h>
#include <thread>

using namespace std;

int main(int argc, char* argv[]) {
    int n = strtoul(argv[1], NULL, 10);

    while (n >= 0) {
        this_thread::sleep_for(1000ms);
        cout << n << endl;
        n--;
    }
}