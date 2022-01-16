package me.cirq.arpmap


sealed class Permission(vararg permissions: String){
    val permissions: List<APermission> = permissions.map{ APermission(it) }
}
class SinglePermission(p: String): Permission(p){
    override fun toString() = permissions[0].toString()
    override fun hashCode() = permissions[0].hashCode()
    override fun equals(other: Any?) = (other is SinglePermission) &&
            (other.permissions[0].hashCode()==permissions[0].hashCode())
}
class AnyOfPermission(ps: List<String>): Permission(*ps.toTypedArray()){
    override fun toString() = permissions.joinToString(", ") + " :: anyOf"
}
class AllOfPermission(ps: List<String>): Permission(*ps.toTypedArray()){
    override fun toString() = permissions.joinToString(", ") + " :: allOf"
}
object DummyPermission: Permission()


class APermission(_name: String) {
    private val name: String = _name.replace(".Manifest", "").replace("#", ".")
    init{
        require(isPermissionString(name))
    }

    override fun toString() = name
    override fun hashCode() = name.hashCode()
    override fun equals(other: Any?) = other.hashCode() == hashCode()

    companion object {
        // the i? is a typo in the source code of `android.companion.CompanionDeviceManager'
        private val PATTERN = """andri?oid\.(contacts\.)?(Manifest\.)?permissions?.([_A-Z0-9])+""".toRegex()
        fun isPermissionString(str: String): Boolean {
            return str.matches(PATTERN) || str in setOf(
                    "NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK",
                    "android.net.NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK"
            )
        }
    }
}
