#ifndef MATH_LIB_H
#define MATH_LIB_H

#ifdef _WIN32
#define EXPORT __declspec(dllexport)
#else
#define EXPORT
#endif

EXPORT int add(int a, int b);

#endif
