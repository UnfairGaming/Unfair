package unfair.module;

import unfair.Unfair;
import unfair.event.EventTarget;
import unfair.event.types.EventType;
import unfair.events.KeyEvent;
import unfair.events.TickEvent;
import unfair.module.modules.render.GuiModule;
import unfair.module.modules.render.HUD;
import unfair.util.ChatUtil;
import unfair.util.SoundUtil;

import java.net.URL;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ModuleManager {
    public final LinkedHashMap<Class<?>, Module> modules = new LinkedHashMap<>();
    private boolean sound = false;

    public ModuleManager() {
        autoRegisterModules();
    }

    private void autoRegisterModules() {
        List<Class<? extends Module>> moduleClasses = new ArrayList<>();

        for (Category category : Category.values()) {
            moduleClasses.addAll(scanPackageForModules(category.getPackageName()));
        }

        moduleClasses.sort(Comparator.comparing(Class::getSimpleName));

        for (Class<? extends Module> clazz : moduleClasses) {
            try {
                Module module = clazz.getDeclaredConstructor().newInstance();
                modules.put(clazz, module);
            } catch (Exception e) {
                System.err.println("Failed to instantiate module: " + clazz.getName());
                e.printStackTrace();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Class<? extends Module>> scanPackageForModules(String packageName) {
        List<Class<? extends Module>> result = new ArrayList<>();
        String path = packageName.replace('.', '/');

        try {
            Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(path);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                if (url.getProtocol().equals("jar")) {
                    // 从 JAR 文件扫描
                    String jarPath = url.getPath().substring(5, url.getPath().indexOf("!"));
                    try (JarFile jar = new JarFile(jarPath)) {
                        Enumeration<JarEntry> entries = jar.entries();
                        while (entries.hasMoreElements()) {
                            JarEntry entry = entries.nextElement();
                            String name = entry.getName();
                            if (name.startsWith(path) && name.endsWith(".class")) {
                                String className = name.replace('/', '.').substring(0, name.length() - 6);
                                try {
                                    Class<?> clazz = Class.forName(className);
                                    if (Module.class.isAssignableFrom(clazz) && !clazz.isInterface() && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                                        result.add((Class<? extends Module>) clazz);
                                    }
                                } catch (ClassNotFoundException ignored) {
                                }
                            }
                        }
                    }
                } else if (url.getProtocol().equals("file")) {
                    // 从文件系统扫描 (开发环境)
                    java.io.File directory = new java.io.File(url.toURI());
                    if (directory.exists() && directory.isDirectory()) {
                        scanDirectoryForModules(directory, packageName, result);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private void scanDirectoryForModules(java.io.File directory, String packageName, List<Class<? extends Module>> result) {
        java.io.File[] files = directory.listFiles();
        if (files == null) return;

        for (java.io.File file : files) {
            if (file.isDirectory()) {
                scanDirectoryForModules(file, packageName + "." + file.getName(), result);
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + "." + file.getName().substring(0, file.getName().length() - 6);
                try {
                    Class<?> clazz = Class.forName(className);
                    if (Module.class.isAssignableFrom(clazz) && !clazz.isInterface() && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                        result.add((Class<? extends Module>) clazz);
                    }
                } catch (ClassNotFoundException ignored) {
                }
            }
        }
    }

    public Module getModule(String string) {
        return this.modules.values().stream().filter(mD -> mD.getName().equalsIgnoreCase(string)).findFirst().orElse(null);
    }

    public Module getModule(Class<?> clazz) {
        return this.modules.get(clazz);
    }

    public List<Module> getModulesByCategory(Category category) {
        List<Module> categoryModules = new ArrayList<>();
        for (Module module : modules.values()) {
            if (module.getCategory() == category) {
                categoryModules.add(module);
            }
        }
        categoryModules.sort(Comparator.comparing(m -> m.getName().toLowerCase()));
        return categoryModules;
    }

    public void playSound() {
        this.sound = true;
    }

    @EventTarget
    public void onKey(KeyEvent event) {
        for (Module module : this.modules.values()) {
            if (module.getKey() != event.getKey()) {
                continue;
            }
            boolean shouldNotify = module.toggle();
            HUD hud = (HUD) this.modules.get(HUD.class);
            if (hud != null && shouldNotify) {
                shouldNotify = hud.toggleAlerts.getValue();
            }
            if (module instanceof GuiModule) {
                shouldNotify = false;
            }
            if (shouldNotify) {
                String status = module.isEnabled() ? "&a&lON" : "&c&lOFF";
                String message = String.format("%s%s: %s&r", Unfair.clientName, module.getName(), status);
                ChatUtil.sendFormatted(message);
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() == EventType.PRE) {
            if (this.sound) {
                this.sound = false;
                SoundUtil.playSound("random.click");
            }
        }
    }
}
