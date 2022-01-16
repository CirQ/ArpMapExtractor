package me.cirq.arpmap

import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.ast.type.Type
import com.github.javaparser.resolution.UnsolvedSymbolException
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserMethodDeclaration


class JMethod private constructor(
        private val name: String,
        private val clazz: String,
        private val ret: String,
        private val params: Array<String>
) {

    private val annotationPermission: MutableSet<Permission> = mutableSetOf()
    private val javadocPermission: MutableSet<Permission> = mutableSetOf()

    private val mappingName: String by lazy {
        "$clazz.$name(${params.joinToString(",")})$ret"
    }

    val aPermission: Set<Permission>
        get() = annotationPermission
    val jPermission: Set<Permission>
        get() = javadocPermission

    fun addAnnotationPermission(permissions: Collection<Permission>?){
        if(permissions != null) {
            val genuinePermission = permissions.filter{ it != DummyPermission }
            annotationPermission.addAll(genuinePermission)
        }
    }

    fun addJavadocPermission(permissions: Collection<Permission>?){
        if(permissions != null) {
            val genuinePermission = permissions.filter{ it != DummyPermission }
            javadocPermission.addAll(genuinePermission)
        }
    }

    companion object {

        private fun Type.isNestedType() = this is ClassOrInterfaceType && this.typeArguments.isPresent

        fun fromMethodDec(m: ResolvedMethodDeclaration, imported: Set<String>): JMethod {

            fun findImported(cls: String): String? {
                return imported.firstOrNull{ it.endsWith(".$cls") }
            }

            fun solveNestedType(type: Type): String {
                check(type is ClassOrInterfaceType)
                val baseName = findImported(type.name.asString())!!
                val resolvedParams = type.typeArguments.get().map{
                    if(it.isNestedType()) {
                        solveNestedType(it)
                    }
                    else {
                        try {
                            it.resolve().describe()
                        } catch (ex: UnsolvedSymbolException) {
                            findImported(it.toString())!!       // if error, see other findImported workaround
                        }
                    }
                }.toList()
                return "$baseName<${resolvedParams.joinToString(",")}>"
            }

            val clazz = "${m.packageName}." + m.className.replace('.', '$')
            val ret = try {
                m.returnType.describe()
            } catch (ex: UnsolvedSymbolException) {
                val mType = (m as JavaParserMethodDeclaration).wrappedNode.type
                if(mType.isNestedType())
                    solveNestedType(mType)
                else
                    findImported(mType.asString()) ?: "${m.packageName}.${mType.asString()}"
            }
            val params = (0 until m.numberOfParams).map { i ->
                val param = m.getParam(i)
                try {
                    var desType = param.describeType()
                    val isVararg = desType.endsWith("...")
                    if(isVararg)
                        desType = desType.replace("""\.{3}$""".toRegex(), "")
                    val prefix = desType.split(".") as MutableList
                    if(prefix.size > 1) {
                        val REPLACE = "$"
//                        val REPLACE = "-".repeat(64)
                        val pCls = prefix.removeAt(prefix.size - 1)
                        if(prefix.last(1)[0].isUpperCase() && pCls[0].isUpperCase())
                            desType = "${prefix.joinToString(".")}$REPLACE$pCls"
                    }
                    if(isVararg)
                        desType += "..."
                    desType
                } catch (ex: UnsolvedSymbolException) {
                    findImported(ex.name) ?: "${m.packageName}.${ex.name}"
                }
            }.toTypedArray()
            return JMethod(m.name, clazz, ret, params)
        }
    }

    override fun toString() = "<$clazz: $ret $name(${params.joinToString(",")})>"


    fun joinAPermission(): String {
        val ap = annotationPermission.joinToString(" ")
        return "$mappingName :: $ap"
    }

    fun joinJPermission(): String {
        val jp = javadocPermission.joinToString(", ")
        return "$mappingName :: $jp"
    }

}

fun <T> List<T>.last(i: Int): T = this[size-i]
