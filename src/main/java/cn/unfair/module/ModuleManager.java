package cn.unfair.module;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.KeyEvent;
import cn.unfair.events.TickEvent;
import cn.unfair.module.modules.render.ClickGui;
import cn.unfair.module.modules.render.HUD;
import cn.unfair.util.ChatUtil;
import cn.unfair.util.SoundUtil;

import java.io.File;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

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
        Set<Class<? extends Module>> result = new LinkedHashSet<>();
        String path = packageName.replace('.', '/');

        try {
            Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(path);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                if ("jar".equals(url.getProtocol())) {
                    JarURLConnection connection = (JarURLConnection) url.openConnection();
                    try (JarFile jar = connection.getJarFile()) {
                        Enumeration<JarEntry> entries = jar.entries();
                        while (entries.hasMoreElements()) {
                            addModuleClass(entries.nextElement().getName(), result);
                        }
                    }
                } else if ("file".equals(url.getProtocol())) {
                    Path directory = new File(url.toURI()).toPath();
                    if (Files.isDirectory(directory)) {
                        try (Stream<Path> files = Files.walk(directory)) {
                            files.filter(Files::isRegularFile)
                                    .filter(file -> file.getFileName().toString().endsWith(".class"))
                                    .forEach(file -> {
                                        String relativeName = directory.relativize(file).toString()
                                                .replace(File.separatorChar, '/');
                                        addModuleClass(path + "/" + relativeName, result);
                                    });
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new ArrayList<>(result);
    }

    @SuppressWarnings("unchecked")
    private void addModuleClass(String entryName, Set<Class<? extends Module>> result) {
        if (!entryName.endsWith(".class") || entryName.endsWith("module-info.class")) {
            return;
        }
        String className = entryName.substring(0, entryName.length() - 6).replace('/', '.');
        try {
            Class<?> clazz = Class.forName(className);
            if (Module.class.isAssignableFrom(clazz)
                    && !clazz.isInterface()
                    && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())
                    && clazz.getEnclosingClass() == null
                    && !SubModule.class.isAssignableFrom(clazz)) {
                result.add((Class<? extends Module>) clazz);
            }
        } catch (ClassNotFoundException ignored) {
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
            if (module instanceof ClickGui) {
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
