package me.cirq.arpmap

import org.w3c.dom.Document
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader
import java.lang.RuntimeException
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPath
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

object ArpPermExtractor {

    private val xPath: XPath by lazy {
        val xpFactory = XPathFactory.newInstance()
        xpFactory.newXPath()
    }

    private fun getDocument(xmlFile: File): Document {
        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()
        val xmlInput = InputSource(StringReader(xmlFile.readText()))
        return dBuilder.parse(xmlInput)
    }

    private fun iterPermNode(doc: Document) = sequence<Node> {
        val permissions = xPath.evaluate("/manifest/permission", doc, XPathConstants.NODESET) as NodeList
        for(i in 0 until permissions.length){
            val node = permissions.item(i)
            yield(node)
        }
    }

    fun extract(sdkVersion: Int, xmlFile: File, outPath: Path) {
        val doc = getDocument(xmlFile)

        val normalPermissions: MutableSet<String> = LinkedHashSet()
        val dangerousPermissions: MutableSet<String> = LinkedHashSet()
        val signaturePermissions: MutableSet<String> = LinkedHashSet()

        var permNum = 0

        for(pnode in iterPermNode(doc)){
            pnode.attributes.apply {
                val permName = getNamedItem("android:name").nodeValue
                val permProtLevel = getNamedItem("android:protectionLevel").nodeValue
                if("dangerous" in permProtLevel && "signature" in permProtLevel){
                    throw RuntimeException("unknown protection level $permProtLevel for $permName")
                }
                when{
                    permProtLevel.startsWith("normal")-> {
                        normalPermissions.add(permName)
                    }
                    permProtLevel.startsWith("dangerous")-> {
                        dangerousPermissions.add(permName)
                    }
                    permProtLevel.startsWith("signature")||permProtLevel.endsWith("signature")-> {
                        signaturePermissions.add(permName)
                    }
                    else-> {
                        println("$permName  $permProtLevel")
                    }
                }
            }
            permNum++
        }

        println("-".repeat(100))
        println("all permissions $permNum")
        println("normal permission ${normalPermissions.size}")
        println("dangerous permission ${dangerousPermissions.size}")
        println("signature permission ${signaturePermissions.size}")
        assert(permNum==(normalPermissions.size+dangerousPermissions.size+signaturePermissions.size))

        val outDir = outPath.resolve("API$sdkVersion")
        outDir.toFile().mkdirs()
        writePermission(normalPermissions, dangerousPermissions, signaturePermissions, outDir)
    }

}