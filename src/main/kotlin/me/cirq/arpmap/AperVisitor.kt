package me.cirq.arpmap

import com.github.javaparser.ast.ImportDeclaration
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.*
import com.github.javaparser.ast.visitor.VoidVisitorAdapter
import com.github.javaparser.javadoc.Javadoc
import com.github.javaparser.javadoc.description.JavadocInlineTag
import java.lang.IllegalArgumentException


class AperVisitor: VoidVisitorAdapter<Any?>() {

    companion object {
        private const val PERMISSION_PREFIX = "android.Manifest.permission#"
        private val PERMISSION_PATTERN = "^(android.Manifest.permissions?)([.#])".toRegex()
        private val S_PERMISSION_PATTERN = "^(Manifest.permissions?)([.#])".toRegex()
        private val SS_PERMISSION_PATTERN = "^(permissions?)([.#])".toRegex()
    }


    private val importedClasses = HashSet<String>()

    override fun visit(n: ImportDeclaration, arg: Any?) {
        importedClasses.add(n.nameAsString)
        super.visit(n, arg)
    }


    private fun normalizePermissionString(ps: String): String {
        var permString = ps.trim('"', '(', ')')
        permString = permString.replace(PERMISSION_PATTERN, """$1#""")
        permString = permString.replace(S_PERMISSION_PATTERN, """android.$1#""")
        permString = permString.replace(SS_PERMISSION_PATTERN, """android.Manifest.$1#""")
        if(permString.matches("[_A-Z]+".toRegex()))
            permString = PERMISSION_PREFIX + ps
        return permString
    }

    private fun fromJavadoc(javadoc: Javadoc): List<Permission> {
        val perms = mutableListOf<Permission>()
        if("Manifest.permission" in javadoc.description.toText()) {
            // extract from description
            javadoc.description.elements.filterIsInstance<JavadocInlineTag>()
                    .filter{ it.type==JavadocInlineTag.Type.LINK && "permission" in it.content }
                    .map{ it.content.trim().replace("""[\n\r]+""".toRegex(), "") }
                    .forEach{ linkTag ->
                        linkTag.split(' ').forEach{
                            var permString = normalizePermissionString(it)
                            if(permString.contains("CONTROL_LOCATION_UPDATESCONTROL_LOCATION_UPDATES")) {
                                // some strange writing in api level 23-25
                                permString = permString.replaceFirst("CONTROL_LOCATION_UPDATES", "")
                            }
                            else if(permString.contains("READ_PRECISE_PHONE_STATEREAD_PRECISE_PHONE_STATE")) {
                                // some strange writing in api level 30
                                permString = permString.replaceFirst("READ_PRECISE_PHONE_STATE", "")
                            }
                            perms += SinglePermission(permString)
                        }
                    }
        }
        javadoc.blockTags.forEach{ blockTag ->
            // extract from block tags
            if("Manifest.permission" in blockTag.toText()) {
                blockTag.content.elements.filterIsInstance<JavadocInlineTag>()
                        .filter{ it.type == JavadocInlineTag.Type.LINK && "permission" in it.content }
                        .map{ it.content.trim().split("\\s+".toRegex()).first() }
                        .forEach{ linkTag ->
                            linkTag.split(' ').forEach{
                                val permString = normalizePermissionString(it)
                                try {
                                    perms += SinglePermission(permString)
                                } catch (_: IllegalArgumentException) {}
                            }
                        }
            }
        }
        return perms
    }

    private fun fromAnnotation(annotation: NodeList<AnnotationExpr>): List<Permission> {
        val perms = mutableListOf<Permission>()
        annotation.forEach{
            if(it.name.toString() == "RequiresPermission") {
                when(it) {
                    is SingleMemberAnnotationExpr -> {
                        val p = it.memberValue.toString()
                        val pVal = normalizePermissionString(p)
                        perms += SinglePermission(pVal)
                    }
                    is NormalAnnotationExpr -> {
                        it.pairs.forEach { pair ->
                            val key = pair.name.identifier
                            perms += if(key == "anyOf") {
                                val value = pair.value as ArrayInitializerExpr
                                val pVals = value.values.map{ v ->
                                    normalizePermissionString(v.toString())
                                }
                                AnyOfPermission(pVals)
                            } else if(key == "allOf") {
                                val value = pair.value as ArrayInitializerExpr
                                val pVals = value.values.map{ v ->
                                    normalizePermissionString(v.toString())
                                }
                                AllOfPermission(pVals)
                            } else if(key == "value") {
                                val value = pair.value as FieldAccessExpr
                                val pVals = normalizePermissionString(value.toString())
                                SinglePermission(pVals)
                            } else {
                                if(key !in setOf("conditional"))
                                    throw NotImplementedError("unknown kw: $key")
                                DummyPermission
                            }
                        }
                    }
                    else -> throw NotImplementedError()
                }
            }
        }
        return perms
    }

    override fun visit(n: MethodDeclaration, arg: Any?) {
        val method = n.resolve()
        val dangerousApis = arg as MutableList<JMethod>
        if(n.annotations.isNonEmpty || n.hasJavaDocComment()) {
            var annotPerms: List<Permission>? = null
            var jdocPerms: List<Permission>? = null

            if(n.annotations.isNonEmpty) {
                annotPerms = fromAnnotation(n.annotations)
            }
            if(n.hasJavaDocComment()) {
                jdocPerms = fromJavadoc(n.javadoc.get())
            }
            if(annotPerms.isNotEmpty() || jdocPerms.isNotEmpty()){
                val jMethod = JMethod.fromMethodDec(method, importedClasses)
                jMethod.addAnnotationPermission(annotPerms)
                jMethod.addJavadocPermission(jdocPerms)
                dangerousApis.add(jMethod)
            }
        }
        super.visit(n, arg)
    }

}


private inline fun <T> Collection<T>?.isNotEmpty(): Boolean = !(this?.isEmpty()?:true)
