#include "utils.h"
#include "err.h"
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <malloc.h>
#include <semaphore.h>
#include <sys/mman.h>
#include <sys/wait.h>

#define MAX_CMD_LEN 512
#define MAX_OUT_LEN 1024
#define MAX_N_TASKS 4096
#define MS_TO_US 1000 // Number of microseconds in one millisecond.

const char *RUN_CMD = "run";
const char *OUT_CMD = "out";
const char *ERR_CMD = "err";
const char *KILL_CMD = "kill";
const char *SLEEP_CMD = "sleep";
const char *QUIT_CMD = "quit";

struct Task {
    char buf_out[MAX_OUT_LEN];
    char buf_err[MAX_OUT_LEN];
    sem_t mutex_out;
    sem_t mutex_err;
    pid_t pid; // Id of the process that executes program.
};

struct StatusInfo {
    size_t task_id;
    int status; // Stored by wait().
};

struct SharedStorage {
    struct Task tasks[MAX_N_TASKS];
    // Stores info about tasks that ended during handling of the command.
    struct StatusInfo queue_terminated[MAX_N_TASKS];
    size_t queue_rear;
    // True if no command is being handled at the time, false otherwise.
    bool can_print_term_msg;
    sem_t mutex_queue;
    sem_t mutex_bool;
    // Used to make the main process wait for execution of the program.
    sem_t mutex_run;
};

size_t task_id = 0;

struct SharedStorage *init_shm() {
    int protection = PROT_READ | PROT_WRITE;
    int visibility = MAP_SHARED | MAP_ANONYMOUS;
    struct SharedStorage *sh_storage = mmap(NULL, sizeof(struct SharedStorage), protection, visibility, -1, 0);
    if (sh_storage == MAP_FAILED) {
        syserr("mmap");
    }

    for (int i = 0; i < MAX_N_TASKS; i++) {
        ASSERT_SYS_OK(sem_init(&sh_storage->tasks[i].mutex_out, 1, 1));
        ASSERT_SYS_OK(sem_init(&sh_storage->tasks[i].mutex_err, 1, 1));
    }

    sh_storage->queue_rear = 0;
    sh_storage->can_print_term_msg = true;
    ASSERT_SYS_OK(sem_init(&sh_storage->mutex_queue, 1, 1));
    ASSERT_SYS_OK(sem_init(&sh_storage->mutex_bool, 1, 1));
    ASSERT_SYS_OK(sem_init(&sh_storage->mutex_run, 1, 0));

    return sh_storage;
}

void cleanup_shm(struct SharedStorage *sh_storage) {
    for (int i = 0; i < MAX_N_TASKS; i++) {
        ASSERT_SYS_OK(sem_destroy(&sh_storage->tasks[i].mutex_out));
        ASSERT_SYS_OK(sem_destroy(&sh_storage->tasks[i].mutex_err));
    }

    ASSERT_SYS_OK(sem_destroy(&sh_storage->mutex_queue));
    ASSERT_SYS_OK(sem_destroy(&sh_storage->mutex_bool));
    ASSERT_SYS_OK(sem_destroy(&sh_storage->mutex_run));

    ASSERT_SYS_OK(munmap(sh_storage, sizeof(struct SharedStorage)));
}

// Helper process that continuously reads 'out' messages sent by the program.
void manage_out(struct SharedStorage *sh_storage, const int pipe_dsc_out[2], const int pipe_dsc_err[2]) {
    pid_t pid = fork();
    ASSERT_SYS_OK(pid);
    if (!pid) {
        // Child process of wrapper that manages stdout.

        ASSERT_SYS_OK(close(pipe_dsc_err[0]));
        ASSERT_SYS_OK(close(pipe_dsc_err[1]));

        int read_dsc = pipe_dsc_out[0];
        int write_dsc = pipe_dsc_out[1];

        ASSERT_SYS_OK(dup2(read_dsc, STDIN_FILENO));
        ASSERT_SYS_OK(close(write_dsc));

        FILE *stream = fdopen(read_dsc, "r");
        char line[MAX_OUT_LEN];

        if (stream == NULL) {
            syserr("fdopen");
        }

        while (read_line(line, MAX_OUT_LEN, stream)) {
            // Mutex is used to prevent reading from this buffer while content is being copied to it.
            ASSERT_SYS_OK(sem_wait(&sh_storage->tasks[task_id].mutex_out));
            strcpy(sh_storage->tasks[task_id].buf_out, line);
            ASSERT_SYS_OK(sem_post(&sh_storage->tasks[task_id].mutex_out));
        }

        fclose(stream);
        _exit(EXIT_SUCCESS);
    }
}

// Helper process that continuously reads 'err' messages sent by the program.
void manage_err(struct SharedStorage *sh_storage, const int pipe_dsc_out[2], const int pipe_dsc_err[2]) {
    pid_t pid = fork();
    ASSERT_SYS_OK(pid);
    if (!pid) {
        // Child process of wrapper that manages stderr.

        ASSERT_SYS_OK(close(pipe_dsc_out[0]));
        ASSERT_SYS_OK(close(pipe_dsc_out[1]));

        int read_dsc = pipe_dsc_err[0];
        int write_dsc = pipe_dsc_err[1];

        ASSERT_SYS_OK(dup2(read_dsc, STDIN_FILENO));
        ASSERT_SYS_OK(close(write_dsc));

        FILE *stream = fdopen(read_dsc, "r");
        char line[MAX_OUT_LEN];

        if (stream == NULL) {
            syserr("fdopen");
        }

        while (read_line(line, MAX_OUT_LEN, stream)) {
            ASSERT_SYS_OK(sem_wait(&sh_storage->tasks[task_id].mutex_err));
            strcpy(sh_storage->tasks[task_id].buf_err, line);
            ASSERT_SYS_OK(sem_post(&sh_storage->tasks[task_id].mutex_err));
        }

        fclose(stream);
        _exit(EXIT_SUCCESS);
    }
}

void execute_program(char **cmd_args, struct SharedStorage *sh_storage, const int pipe_dsc_out[2],
                     const int pipe_dsc_err[2]) {
    int read_dsc_out = pipe_dsc_out[0];
    int write_dsc_out = pipe_dsc_out[1];
    int read_dsc_err = pipe_dsc_err[0];
    int write_dsc_err = pipe_dsc_err[1];

    pid_t pid = fork();
    ASSERT_SYS_OK(pid);
    if (!pid) {
        ASSERT_SYS_OK(close(read_dsc_out));
        ASSERT_SYS_OK(close(read_dsc_err));

        ASSERT_SYS_OK(dup2(write_dsc_out, STDOUT_FILENO));
        ASSERT_SYS_OK(dup2(write_dsc_err, STDERR_FILENO));

        sh_storage->tasks[task_id].pid = getpid();
        // We're releasing the mutex, so that main process can resume.
        ASSERT_SYS_OK(sem_post(&sh_storage->mutex_run));

        ASSERT_SYS_OK(execvp(cmd_args[0], cmd_args));
    } else {
        ASSERT_SYS_OK(close(read_dsc_out));
        ASSERT_SYS_OK(close(write_dsc_out));
        ASSERT_SYS_OK(close(read_dsc_err));
        ASSERT_SYS_OK(close(write_dsc_err));

        int status;
        // Wait for program to die.
        ASSERT_SYS_OK(waitpid(pid, &status, 0));
        // Wait for out and err handlers to die.
        ASSERT_SYS_OK(wait(NULL));
        ASSERT_SYS_OK(wait(NULL));

        ASSERT_SYS_OK(sem_wait(&sh_storage->mutex_bool));
        if (sh_storage->can_print_term_msg) {
            if (WIFEXITED(status)) {
                printf("Task %zu ended: status %d.\n", task_id, WEXITSTATUS(status));
            } else if (WIFSIGNALED(status)) {
                printf("Task %zu ended: signalled.\n", task_id);
            }
            fflush(stdout);
        } else {
            ASSERT_SYS_OK(sem_wait(&sh_storage->mutex_queue));
            sh_storage->queue_terminated[sh_storage->queue_rear].task_id = task_id;
            sh_storage->queue_terminated[sh_storage->queue_rear].status = status;
            (sh_storage->queue_rear)++;
            ASSERT_SYS_OK(sem_post(&sh_storage->mutex_queue));
        }
        ASSERT_SYS_OK(sem_post(&sh_storage->mutex_bool));

        _exit(EXIT_SUCCESS);
    }
}

void run(char **cmd_args, struct SharedStorage *sh_storage) {
    pid_t pid = fork();
    ASSERT_SYS_OK(pid);
    if (!pid) {
        int pipe_dsc_out[2];
        int pipe_dsc_err[2];
        ASSERT_SYS_OK(pipe(pipe_dsc_out));
        ASSERT_SYS_OK(pipe(pipe_dsc_err));

        manage_out(sh_storage, pipe_dsc_out, pipe_dsc_err);
        manage_err(sh_storage, pipe_dsc_out, pipe_dsc_err);
        execute_program(cmd_args, sh_storage, pipe_dsc_out, pipe_dsc_err);
    } else {
        // Main process waits for the program to start.
        ASSERT_SYS_OK(sem_wait(&sh_storage->mutex_run));
        printf("Task %zu started: pid %d.\n", task_id, sh_storage->tasks[task_id].pid);
        task_id++;
    }
}

// Prints status of tasks that ended during handling of the command
// and were pushed into the queue.
void manage_terminated(struct SharedStorage *sh_storage) {
    ASSERT_SYS_OK(sem_wait(&sh_storage->mutex_queue));
    for (size_t i = 0; i < sh_storage->queue_rear; i++) {
        size_t term_task_id = sh_storage->queue_terminated[i].task_id;
        int term_status = sh_storage->queue_terminated[i].status;

        if (WIFEXITED(term_status)) {
            printf("Task %zu ended: status %d.\n", term_task_id, WEXITSTATUS(term_status));
        } else if (WIFSIGNALED(term_status)) {
            printf("Task %zu ended: signalled.\n", term_task_id);
        }
    }
    sh_storage->queue_rear = 0;
    ASSERT_SYS_OK(sem_post(&sh_storage->mutex_queue));
}

// Sets the flag that determines whether command is being processed.
void set_print_term_msg_flag(struct SharedStorage *sh_storage, bool value) {
    ASSERT_SYS_OK(sem_wait(&sh_storage->mutex_bool));
    sh_storage->can_print_term_msg = value;
    ASSERT_SYS_OK(sem_post(&sh_storage->mutex_bool));
}

int main(void) {
    setbuf(stdin, 0);
    setbuf(stdout, 0);

    struct SharedStorage *sh_storage = init_shm();

    char cmd[MAX_CMD_LEN];
    while (read_line(cmd, MAX_CMD_LEN, stdin)) {
        // Start of processing of the command.
        // Termination messages cannot be printed.
        set_print_term_msg_flag(sh_storage, false);

        char **cmd_parts = split_string(cmd);
        if (strcmp(cmd_parts[0], RUN_CMD) == 0) {
            // 1. RUN
            run(&cmd_parts[1], sh_storage);
        } else if (strcmp(cmd_parts[0], OUT_CMD) == 0) {
            // 2. OUT
            size_t target_task_id = strtol(cmd_parts[1], NULL, 10);
            ASSERT_SYS_OK(sem_wait(&sh_storage->tasks[target_task_id].mutex_out));
            printf("Task %zu stdout: '%s'.\n", target_task_id, sh_storage->tasks[target_task_id].buf_out);
            ASSERT_SYS_OK(sem_post(&sh_storage->tasks[target_task_id].mutex_out));
        } else if (strcmp(cmd_parts[0], ERR_CMD) == 0) {
            // 3. ERR
            size_t target_task_id = strtol(cmd_parts[1], NULL, 10);
            ASSERT_SYS_OK(sem_wait(&sh_storage->tasks[target_task_id].mutex_err));
            printf("Task %zu stderr: '%s'.\n", target_task_id, sh_storage->tasks[target_task_id].buf_err);
            ASSERT_SYS_OK(sem_post(&sh_storage->tasks[target_task_id].mutex_err));
        } else if (strcmp(cmd_parts[0], KILL_CMD) == 0) {
            // 4. KILL
            size_t target_task_id = strtol(cmd_parts[1], NULL, 10);
            pid_t target_pid = sh_storage->tasks[target_task_id].pid;
            kill(target_pid, SIGINT);
        } else if (strcmp(cmd_parts[0], SLEEP_CMD) == 0) {
            // 5. SLEEP
            size_t nap_time_in_ms = strtol(cmd_parts[1], NULL, 10);
            usleep(MS_TO_US * nap_time_in_ms);
        } else if (strcmp(cmd_parts[0], QUIT_CMD) == 0) {
            // 6. QUIT
            manage_terminated(sh_storage);
            set_print_term_msg_flag(sh_storage, true);
            free_split_string(cmd_parts);
            break;
        }

        manage_terminated(sh_storage);
        set_print_term_msg_flag(sh_storage, true);
        free_split_string(cmd_parts);
    }


    for (size_t i = 0; i < task_id; i++) {
        pid_t target_pid = sh_storage->tasks[i].pid;
        kill(target_pid, SIGKILL);
    }

    for (int i = 0; i < task_id; i++) {
        // Wait for the wrapper processes to die.
        wait(NULL);
    }

    manage_terminated(sh_storage);
    cleanup_shm(sh_storage);

    return 0;
}
