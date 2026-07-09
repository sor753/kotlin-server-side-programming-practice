package com.shou.demo.domain.user

interface UserRepository {
    fun findByEmail(email: String): User?
}
