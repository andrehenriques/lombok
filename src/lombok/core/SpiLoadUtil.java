/*
 * Copyright © 2009 Reinier Zwitserloot and Roel Spilker.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package lombok.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import lombok.Lombok;

/**
 * The java core libraries have a SPI discovery system, but it works only in Java 1.6 and up. For at least Eclipse,
 * lombok actually works in java 1.5, so we've rolled our own SPI discovery system.
 * 
 * It is not API compatible with <code>ServiceLoader</code>.
 * 
 * @see java.util.ServiceLoader
 */
public class SpiLoadUtil {
	private SpiLoadUtil() {
		//Prevent instantiation
	}
	
	/**
	 * Returns an iterator of instances that, at least according to the spi discovery file, are implementations
	 * of the stated class.
	 * 
	 * Like ServiceLoader, each listed class is turned into an instance by calling the public no-args constructor.
	 * 
	 * Convenience method that calls the more elaborate {@link #findServices(Class, ClassLoader)} method with
	 * this {@link java.lang.Thread}'s context class loader as <code>ClassLoader</code>.
	 * 
	 * @param target class to find implementations for.
	 */
	public static <C> Iterable<C> findServices(Class<C> target) throws IOException {
		return findServices(target, Thread.currentThread().getContextClassLoader());
	}
	
	/**
	 * Returns an iterator of class objects that, at least according to the spi discovery file, are implementations
	 * of the stated class.
	 * 
	 * Like ServiceLoader, each listed class is turned into an instance by calling the public no-args constructor.
	 * 
	 * @param target class to find implementations for.
	 * @param loader The classloader object to use to both the spi discovery files, as well as the loader to use
	 * to make the returned instances.
	 */
	public static <C> Iterable<C> findServices(final Class<C> target, final ClassLoader loader) throws IOException {
		Enumeration<URL> resources = loader.getResources("META-INF/services/" + target.getName());
		final Set<String> entries = new LinkedHashSet<String>();
		while (resources.hasMoreElements()) {
			URL url = resources.nextElement();
			readServicesFromUrl(entries, url);
		}
		
		final Iterator<String> names = entries.iterator();
		return new Iterable<C> () {
			@Override public Iterator<C> iterator() {
				return new Iterator<C>() {
					@Override public boolean hasNext() {
						return names.hasNext();
					}
					
					@Override public C next() {
						try {
							return target.cast(Class.forName(names.next(), true, loader).newInstance());
						} catch (Throwable t) {
							throw Lombok.sneakyThrow(t);
						}
					}
					
					@Override public void remove() {
						throw new UnsupportedOperationException();
					}
				};
			}
		};
	}
	
	private static void readServicesFromUrl(Collection<String> list, URL url) throws IOException {
		InputStream in = url.openStream();
		try {
			if (in == null) return;
			BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
			while (true) {
				String line = r.readLine();
				if (line == null) break;
				int idx = line.indexOf('#');
				if (idx != -1) line = line.substring(0, idx);
				line = line.trim();
				if (line.length() == 0) continue;
				list.add(line);
			}
		} finally {
			try {
				in.close();
			} catch (Throwable ignore) {}
		}
	}
	
	/**
	 * This method will find the <code>T</code> in <code>public class Foo extends BaseType&lt;T&gt;.
	 * 
	 * It returns an annotation type because it is used exclusively to figure out which annotations are
	 * being handled by {@link lombok.eclipse.EclipseAnnotationHandler} and {@link lombok.javac.JavacAnnotationHandler}.
	 */
	@SuppressWarnings("unchecked")
	public static Class<? extends Annotation> findAnnotationClass(Class<?> c, Class<?> base) {
		if (c == Object.class || c == null) return null;
		for (Type iface : c.getGenericInterfaces()) {
			if (iface instanceof ParameterizedType) {
				ParameterizedType p = (ParameterizedType)iface;
				if (!base.equals(p.getRawType())) continue;
				Type target = p.getActualTypeArguments()[0];
				if (target instanceof Class<?>) {
					if (Annotation.class.isAssignableFrom((Class<?>) target)) {
						return (Class<? extends Annotation>) target;
					}
				}
				
				throw new ClassCastException("Not an annotation type: " + target);
			}
		}
		
		Class<? extends Annotation> potential = findAnnotationClass(c.getSuperclass(), base);
		if (potential != null) return potential;
		for (Class<?> iface : c.getInterfaces()) {
			potential = findAnnotationClass(iface, base);
			if (potential != null) return potential;
		}
		
		return null;
	}
}
