package com.duy.ide.autocomplete.dex;

import android.support.annotation.Nullable;
import android.util.Log;

import com.duy.ide.autocomplete.model.ClassDescription;
import com.duy.ide.autocomplete.model.ConstructorDescription;
import com.duy.ide.autocomplete.model.FieldDescription;
import com.duy.ide.autocomplete.model.MethodDescription;
import com.duy.project.file.android.AndroidProjectFolder;
import com.duy.project.file.java.JavaProjectFolder;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import dalvik.system.DexClassLoader;

/**
 * Created by Duy on 20-Jul-17.
 */

public class JavaClassReader {
    private static final String TAG = "JavaClassReader";
    private String classpath;
    private String tempDir;
    private HashMap<String, Class> mClasses = new HashMap<>();
    private WeakHashMap<String, ClassDescription> mCache = new WeakHashMap<>();

    private boolean loaded = false;

    public JavaClassReader(String classpath, String tempDir) {
        this.classpath = classpath;
        this.tempDir = tempDir;
    }

    public HashMap<String, Class> getClasses() {
        return mClasses;
    }

    public HashMap<String, Class> getAllClassesFromProject(boolean android, @Nullable File[] libs) {
        HashMap<String, Class> classes = new HashMap<>();
        if (classpath != null) classes.putAll(getAllClassesFromJar(android, classpath));
        if (libs != null) {
            for (File lib : libs) {
                if (lib.getPath().endsWith(".jar")) {
                    classes.putAll(getAllClassesFromJar(android, lib.getPath()));
                }
            }
        }
        return classes;
    }

    private HashMap<String, Class> getAllClassesFromJar(boolean android, String path) {
        DexClassLoader dexClassLoader = new DexClassLoader(path, tempDir, null, ClassLoader.getSystemClassLoader());
        HashMap<String, Class> classes = new HashMap<>();
        try {
            JarFile jarFile = new JarFile(path);
            Enumeration<JarEntry> e = jarFile.entries();

            while (e.hasMoreElements()) {
                JarEntry je = e.nextElement();
                if (je.isDirectory() || !je.getName().endsWith(".class")) {
                    continue;
                }
                String className = je.getName().substring(0, je.getName().length() - 6);
                className = className.replace('/', '.');
                try {
                    if (android) {
                        Class c = dexClassLoader.loadClass(className);
                        classes.put(c.getName(), c);
                    } else if (!className.startsWith("android")) {
                        Class c = dexClassLoader.loadClass(className);
                        classes.put(c.getName(), c);
                    }
                } catch (ClassNotFoundException e1) {
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return classes;
    }

    public void load(JavaProjectFolder projectFolder) {
        if (loaded) {
            return;
        }
        this.mClasses.clear();
        this.mClasses.putAll(getAllClassesFromProject(
                projectFolder instanceof AndroidProjectFolder, //is android
                projectFolder.getDirLibs().listFiles()));
        loaded = true;
    }

    public void dispose() {
        mClasses.clear();
    }

    @Nullable
    public ClassDescription readClassByName(String className) {
        ClassDescription cache = mCache.get(className);
        if (cache != null) {
            return cache;
        }
        Class aClass = mClasses.get(className);
        Log.d(TAG, "readClassByName() called with: className = [" + className + "]");

        if (aClass != null) {
            String superclass = aClass.getSuperclass() != null ? aClass.getSuperclass().getName() : "";
            ClassDescription classDesc = new ClassDescription(aClass.getSimpleName(), aClass.getName(), superclass, 0);
            for (Constructor constructor : aClass.getConstructors()) {
                if (Modifier.isPublic(constructor.getModifiers())) {
                    classDesc.addConstructor(new ConstructorDescription(constructor));
                }
            }
            for (Field field : aClass.getDeclaredFields()) {
                if (Modifier.isPublic(field.getModifiers())) {
                    if (!field.getName().equals(field.getDeclaringClass().getName())) {
                        classDesc.addField(new FieldDescription(field));
                    }
                }
            }
            for (Method method : aClass.getMethods()) {
                if (Modifier.isPublic(method.getModifiers())) {
                    classDesc.addMethod(new MethodDescription(method));
                }
            }
            mCache.put(className, classDesc);
            return classDesc;
        }
        return null;
    }

    public ArrayList<ClassDescription> findClass(String simpleNamePrefix) {
        ArrayList<ClassDescription> classDescriptions = new ArrayList<>();
        for (Map.Entry<String, Class> entry : mClasses.entrySet()) {
            if (entry.getValue().getSimpleName().startsWith(simpleNamePrefix)) {
                classDescriptions.add(new ClassDescription(entry.getValue()));
            }
        }
        return classDescriptions;
    }

    public boolean isLoaded() {
        return loaded;
    }
}
