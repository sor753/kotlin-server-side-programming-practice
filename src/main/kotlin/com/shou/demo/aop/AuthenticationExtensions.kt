package com.shou.demo.aop

import com.shou.demo.infrastructure.security.BookManagerUserDetails
import org.springframework.security.core.Authentication

fun Authentication?.asBookManagerUserDetails(): BookManagerUserDetails? = this?.principal as? BookManagerUserDetails
