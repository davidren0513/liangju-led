#pragma once
#include <string>
void reset_cancel();
void cancel_generation();
std::string generate(const std::string & model_path, const std::string & prompt);
