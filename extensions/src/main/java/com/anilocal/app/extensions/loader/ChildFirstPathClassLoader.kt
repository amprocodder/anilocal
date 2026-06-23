package com.anilocal.app.extensions.loader

import dalvik.system.PathClassLoader
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.util.Enumeration

/**
 * Vendored from aniyomiorg/aniyomi (eu.kanade.tachiyomi.util.system.ChildFirstPathClassLoader).
 *
 * A parent-last class loader that tries, in order: the system class loader, the extension's own dex
 * (child), then the parent (host). This lets an extension use any libraries it bundles, while the
 * Aniyomi source-api classes it does NOT bundle (compileOnly) fall through to the host's vendored
 * copies in [com.anilocal.app.extensions].
 */
class ChildFirstPathClassLoader(
    dexPath: String,
    librarySearchPath: String?,
    parent: ClassLoader,
) : PathClassLoader(dexPath, librarySearchPath, parent) {

    private val systemClassLoader: ClassLoader? = getSystemClassLoader()

    override fun loadClass(name: String?, resolve: Boolean): Class<*> {
        var c = findLoadedClass(name)

        if (c == null && systemClassLoader != null) {
            try {
                c = systemClassLoader.loadClass(name)
            } catch (_: ClassNotFoundException) {}
        }

        if (c == null) {
            c = try {
                findClass(name)
            } catch (_: ClassNotFoundException) {
                super.loadClass(name, resolve)
            }
        }

        if (resolve) {
            resolveClass(c)
        }

        return c
    }

    override fun getResource(name: String?): URL? {
        return systemClassLoader?.getResource(name)
            ?: findResource(name)
            ?: super.getResource(name)
    }

    override fun getResources(name: String?): Enumeration<URL> {
        val systemUrls = systemClassLoader?.getResources(name)
        val localUrls = findResources(name)
        val parentUrls = parent?.getResources(name)
        val urls = buildList {
            while (systemUrls?.hasMoreElements() == true) {
                add(systemUrls.nextElement())
            }
            while (localUrls?.hasMoreElements() == true) {
                add(localUrls.nextElement())
            }
            while (parentUrls?.hasMoreElements() == true) {
                add(parentUrls.nextElement())
            }
        }

        return object : Enumeration<URL> {
            val iterator = urls.iterator()

            override fun hasMoreElements() = iterator.hasNext()
            override fun nextElement() = iterator.next()
        }
    }

    override fun getResourceAsStream(name: String?): InputStream? {
        return try {
            getResource(name)?.openStream()
        } catch (_: IOException) {
            null
        }
    }
}
