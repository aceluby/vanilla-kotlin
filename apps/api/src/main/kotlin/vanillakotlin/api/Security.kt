package vanillakotlin.api

import javax.naming.ldap.LdapName

// using AD groups most accounts have for easier testing/auth. normally these will be specific to your app.
val STANDARD_USER_ROLE = LdapName("CN=APP-GHE-WRITE,OU=Application,OU=Groupings,DC=corp,DC=test,DC=com")
val ADMIN_USER_ROLE = LdapName("CN=APP-CONFLUENCE-USER,OU=Application,OU=Groupings,DC=corp,DC=test,DC=com")

// This class returns an http4k Filter, see here for docs https://www.http4k.org/guide/reference/core/#filters
// typical application security can use a helper class for authorization checks.
// if we needed more customized control, we would implement our own security filter
val standardUserSecurity = RoleCheckAny(listOf(STANDARD_USER_ROLE))

val adminUserSecurity = RoleCheckAny(listOf(ADMIN_USER_ROLE))
