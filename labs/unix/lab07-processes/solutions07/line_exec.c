#include <stdio.h>
#include <stdlib.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

#include "err.h"

#define BUFFER_SIZE 128

int main(int argc, char* argv[])
{
    int n_children = 5;
    if (argc > 2)
        fatal("Expected zero or one arguments, got: %d.", argc - 1);
    if (argc == 2)
        n_children = atoi(argv[1]);

    printf("My pid is %d, my parent's pid is %d\n", getpid(), getppid());

    pid_t pid;
    if (n_children > 0) {
        ASSERT_SYS_OK(pid = fork());
        if (!pid) {
            char buffer[BUFFER_SIZE];
            int ret = snprintf(buffer, sizeof buffer, "%d", n_children - 1);
            if (ret < 0 || ret >= (int)sizeof(buffer))
                fatal("snprintf failed");
            ASSERT_SYS_OK(execl("/home/alek/pw/lab/lab07/solutions07/cmake-build-debug/line_exec", "line_exec", buffer,  NULL));
        }
    }

    if (pid) {
        wait(0);
    }
    printf("My pid is %d, exiting.\n", getpid());

    return 0;
}
