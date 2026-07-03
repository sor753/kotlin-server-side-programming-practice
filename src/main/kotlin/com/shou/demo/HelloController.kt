package com.shou.demo

import org.springframework.stereotype.Controller
import org.springframework.stereotype.Component
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody


interface Greeter {
    fun sayHello(name: String): String
}

@Component
class GreeterImpl : Greeter {
    override fun sayHello(name: String)="Hello, $name!"
}

@Controller
class HelloController {
    @GetMapping("/")
    fun index(model: Model): String {
        model.addAttribute("message", "Hello, World!")
        return "index"
    }
}

@RestController
@RequestMapping("greeter")
class GreeterController (
    private val greeter: Greeter
) {
    @GetMapping("/hello")
    fun hello(@RequestParam("name") name: String): HelloResponse {
        return HelloResponse("Hello, $name!")
    }

    @GetMapping("/hello/{name}")
    fun helloPath(@PathVariable("name") name: String): HelloResponse {
        return HelloResponse("Hello, $name!")
    }

    @PostMapping("/hello")
    fun helloByPost(@RequestBody request: HelloRequest): HelloResponse {
        return HelloResponse("Hello, ${request.name}!")
    }

    @GetMapping("/hello/byservice/{name}")
    fun helloByService(@PathVariable("name") name: String): HelloResponse {
        return HelloResponse(greeter.sayHello(name))
    }
}

data class HelloResponse(val message: String)
data class HelloRequest(val name: String)