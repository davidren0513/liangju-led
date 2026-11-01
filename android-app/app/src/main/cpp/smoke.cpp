#include "engine.h"
#include <fstream>
#include <iostream>
#include <iterator>
int main(int argc, char ** argv) {
    if (argc != 3) return 2;
    std::ifstream f(argv[2]);
    std::string prompt((std::istreambuf_iterator<char>(f)), std::istreambuf_iterator<char>());
    try { reset_cancel(); std::cout << generate(argv[1], prompt) << std::endl; }
    catch (const std::exception & e) { std::cerr << e.what() << std::endl; return 1; }
}
