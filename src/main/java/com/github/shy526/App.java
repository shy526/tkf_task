package com.github.shy526;

import com.github.shy526.task.Task;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;


/**
 * Hello world!
 */
public class App {
    public static void main(String[] args) {
        Package aPackage = App.class.getPackage();
        String packagePath = aPackage.getName().replace(".", "/");
        URL location = App.class.getProtectionDomain().getCodeSource().getLocation();
        String protocol = location.getProtocol();
        List<Task> tasks = new ArrayList<>();
        if ("jar".equals(protocol)) {
            try {
                JarFile jarFile = ((JarURLConnection) location.openConnection()).getJarFile();
                String taskPackPath = packagePath + "/task";
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry jarEntry = entries.nextElement();
                    if (jarEntry.isDirectory()) {
                        continue;
                    }
                    String name = jarEntry.getName();
                    if (!name.equals(taskPackPath) && name.contains(taskPackPath)) {
                        name = name.substring(name.lastIndexOf(taskPackPath));
                        String classPath = name.replaceAll("\\\\", "/").replace(location.getPath().substring(1), "");
                        String className = classPath.substring(0, classPath.lastIndexOf(".")).replaceAll("/", ".");
                        Class<?> aClass = Class.forName(className);
                        if (!aClass.isInterface() && Task.class.isAssignableFrom(aClass)) {
                            tasks.add((Task) aClass.newInstance());
                        }
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            String url = location.getPath() + packagePath;
            File file = new File(url);
            File[] packs = file.listFiles(File::isDirectory);
            if (packs == null) {
                return;
            }
            Arrays.stream(packs).filter(item -> item.getName().equals("task")).findAny().ifPresent(pack -> {
                File[] calzzFile = pack.listFiles(File::isFile);
                if (calzzFile == null) {
                    return;
                }
                for (File item : calzzFile) {
                    System.out.println("item = " + item.getAbsolutePath());
                    String classPath = item.getAbsolutePath().replaceAll("\\\\", "/").replace(location.getPath().substring(1), "");
                    System.out.println("classPath = " + classPath);
                    String className = classPath.substring(0, classPath.lastIndexOf(".")).replaceAll("/", ".");
                    System.out.println("className = " + className);
                    try {
                        Class<?> aClass = Class.forName(className);
                        if (!aClass.isInterface() && Task.class.isAssignableFrom(aClass)) {
                            tasks.add((Task) aClass.newInstance());
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });

            for (Task task : tasks) {
                task.run();
            }
        }

    }
}
