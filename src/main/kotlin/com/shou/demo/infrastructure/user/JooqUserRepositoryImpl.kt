package com.shou.demo.infrastructure.user

import com.shou.demo.domain.user.RoleType
import com.shou.demo.domain.user.User
import com.shou.demo.domain.user.UserRepository
import com.shou.demo.jooq.tables.references.USER
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository

@Repository
class JooqUserRepositoryImpl(
    private val dsl: DSLContext,
) : UserRepository {
    override fun findByEmail(email: String): User? =
        dsl
            .select()
            .from(USER)
            .where(USER.EMAIL.eq(email))
            .fetchOne(::toUser)

    override fun findById(id: Long): User? =
        dsl
            .select()
            .from(USER)
            .where(USER.ID.eq(id))
            .fetchOne(::toUser)

    private fun toUser(record: Record): User =
        User(
            id = record[USER.ID]!!,
            email = record[USER.EMAIL]!!,
            password = record[USER.PASSWORD]!!,
            name = record[USER.NAME]!!,
            roleType = RoleType.valueOf(record[USER.ROLE_TYPE]!!.literal),
        )
}
