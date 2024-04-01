#include <stdio.h>
#include <stdlib.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

#include "err.h"


int main(int argc, char* argv[])
{
    int n_children = 5;
    if (argc > 2)
        fatal("Expected zero or one arguments, got: %d.", argc - 1);
    if (argc == 2)
        n_children = atoi(argv[1]);

    printf("My pid is %d, my parent's pid is %d\n", getpid(), getppid());

    pid_t pid = 0;
    for (int i = 0; i < n_children; i++) {
        if (!pid) {
            ASSERT_SYS_OK(pid = fork());
        } else {
            break;
        }
    }
    if (pid) {
        ASSERT_SYS_OK(wait(0));
    }
    printf("My pid is %d, exiting.\n", getpid());

    return 0;
}
