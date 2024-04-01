#include<bits/stdc++.h>
#include <thread>

using namespace std;

int main(int argc, char* argv[]) {
    int n = strtoul(argv[1], NULL, 10);
    int n0 = strtoul(argv[2], NULL, 10);
    int r = strtoul(argv[3], NULL, 10);

    
    for (int i = 0; i < n; i++) {
        this_thread::sleep_for(1000ms);
        cout << n0 << endl;
        n0 += r;
    }
}