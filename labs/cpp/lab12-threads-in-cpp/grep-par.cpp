#include <iostream>
#include <fstream>
#include <locale>
#include <string>
#include <list>
#include <codecvt>
#include <future>
#include <vector>
#include <thread>

int grep(std::string filename, std::wstring word) {
    std::locale loc("pl_PL.UTF-8");
    std::wfstream file(filename);
    file.imbue(loc);
    std::wstring line;
    unsigned int count = 0;
    while (getline(file, line)) {
        for (auto pos = line.find(word,0);
             pos != std::string::npos;
             pos = line.find(word, pos+1))
            count++;
    }
    return count;
}

void list_grep(const std::list<std::string>& filenames, std::wstring word, std::promise<unsigned int>& len_promise) {
    // TODO: implement
    unsigned int num_words = 0;
    for (const auto& filename : filenames) {
        num_words += grep(filename, word);
    }
    len_promise.set_value(num_words);
}

int main() {
    const unsigned int thread_count = 4;
    std::ios::sync_with_stdio(false); // block C-style IO, as streams will have a different locale
    std::locale loc("pl_PL.UTF-8");
    std::wcout.imbue(loc); // put our locale on I/O streams
    std::wcin.imbue(loc);

    std::wstring word;
    std::getline(std::wcin, word);

    std::wstring s_file_count;
    std::getline(std::wcin, s_file_count);
    int file_count = std::stoi(s_file_count);

    std::vector<std::list<std::string>> lists_filenames{};
    for (unsigned int thread = 0; thread < thread_count; thread++) {
        lists_filenames.push_back(std::list<std::string>{});
    }
    std::wstring_convert<std::codecvt_utf8<wchar_t>, wchar_t> converter;

    for (int file_num = 0, thread = 0;
         file_num < file_count;
         file_num++, thread = (thread + 1) % thread_count) {
        std::wstring w_filename;
        std::getline(std::wcin, w_filename);
        std::string s_filename = converter.to_bytes(w_filename);
        lists_filenames[thread].push_back(s_filename);
    }

    // TODO: create and start threads
    std::vector<std::promise<unsigned int>> len_promises(thread_count);
    std::vector<std::future<unsigned int>> len_futures(thread_count);


    std::vector<std::thread> threads;

    for (unsigned int i = 0; i < thread_count; i++) {
        std::promise<unsigned int> len_promise;
        len_promises[i] = std::move(len_promise);
        std::future<unsigned int> len_future = len_promises[i].get_future();
        len_futures[i] = std::move(len_future);
        threads.emplace_back([&lists_filenames, word, &len_promises, i]{ list_grep(lists_filenames[i], word, len_promises[i]);});
    }

    // TODO: collect results from threads
    unsigned int count = 0;
    for (unsigned int i = 0; i < thread_count; i++) {
        count += len_futures[i].get();
    }

    for (unsigned int i = 0; i < thread_count; i++) {
        threads[i].join();
    }

    std::wcout << count << std::endl;
}
