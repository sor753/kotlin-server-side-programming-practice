package com.shou.demo.infrastructure.security
import com.shou.demo.domain.user.RoleType
import com.shou.demo.domain.user.User
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException

class BookManagerUserDetailsService(
    private val authenticationUsecase: AuthenticationUsecase,
) : UserDetailsService {
    // loadUserByUsername: ユーザー名（この場合はメールアドレス）をもとにユーザー情報を取得し、UserDetailsとして返す
    // loadUserByUsername で取得した UserDetails 型のオブジェクトを使用して、パスワードの比較や認可処理をフレームワーク側で自動で行う
    override fun loadUserByUsername(username: String): UserDetails {
        val user =
            authenticationUsecase.findUser(username)
                ?: throw UsernameNotFoundException("User not found: $username")
        return BookManagerUserDetails(user)
    }
}

// ログイン時に入力した値から取得したユーザーデータを格納し、認証処理で使用されるもの
// また、認証後はログイン中のユーザー情報としてセッションに保存されるもの
data class BookManagerUserDetails(
    val id: Long,
    val email: String,
    val pass: String,
    val roleType: RoleType,
) : UserDetails {
    constructor(user: User) : this(user.id, user.email, user.password, user.roleType)

    // 権限の取得（複数の権限を保持することも可能）。認可が必要なパスの場合、この関数で取得した権限の情報でチェックされる
    override fun getAuthorities(): MutableCollection<out GrantedAuthority> = AuthorityUtils.createAuthorityList(this.roleType.toString())

    override fun isEnabled(): Boolean = true

    override fun getUsername(): String = this.email

    override fun isCredentialsNonExpired(): Boolean = true

    // ログイン時に入力したパスワードとの比較に使用される
    override fun getPassword(): String = this.pass

    override fun isAccountNonExpired(): Boolean = true

    override fun isAccountNonLocked(): Boolean = true
}
