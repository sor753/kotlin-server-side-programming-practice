package com.shou.demo

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DemoApplication

// mainは起動時に1回だけ呼ばれ、argsも小さいためスプレッド演算子のコピーコストは無視できる
@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<DemoApplication>(*args)
}
