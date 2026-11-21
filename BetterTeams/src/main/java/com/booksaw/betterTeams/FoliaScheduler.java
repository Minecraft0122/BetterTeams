package com.booksaw.betterTeams;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Central scheduler bridge so the plugin also runs on Folia without linking to Paper-only APIs. */
public final class FoliaScheduler {
    private FoliaScheduler() {}

    private static boolean folia() {
        return hasMethod(Bukkit.getServer().getClass(), "getGlobalRegionScheduler");
    }

    public static void run(Plugin plugin, Runnable task) {
        if (!folia()) { Bukkit.getScheduler().runTask(plugin, task); return; }
        invokeGlobal("run", plugin, consumer(task));
    }

    public static void runLater(Plugin plugin, Runnable task, long delay) {
        if (!folia()) { Bukkit.getScheduler().runTaskLater(plugin, task, delay); return; }
        invokeGlobal("runDelayed", plugin, consumer(task), delay);
    }

    public static void runTimer(Plugin plugin, Runnable task, long delay, long period) {
        if (!folia()) { Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period); return; }
        invokeGlobal("runAtFixedRate", plugin, consumer(task), delay, period);
    }

    public static void runAsyncTimer(Plugin plugin, Runnable task, long delay, long period) {
        if (!folia()) { Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, ignored -> task.run(), delay, period); return; }
        Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "BetterTeams-update"); t.setDaemon(true); return t; })
                .scheduleAtFixedRate(() -> run(plugin, task), delay * 50L, period * 50L, TimeUnit.MILLISECONDS);
    }

    public static void runAsync(Plugin plugin, Runnable task) {
        if (!folia()) { Bukkit.getScheduler().runTaskAsynchronously(plugin, task); return; }
        Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "BetterTeams-async"); t.setDaemon(true); return t; }).execute(task);
    }

    public static void runOnEntity(Plugin plugin, Entity entity, Runnable task) {
        if (!folia()) { run(plugin, task); return; }
        try {
            Object scheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
            Method method = scheduler.getClass().getMethod("run", Plugin.class, Consumer.class, Runnable.class);
            method.invoke(scheduler, plugin, consumer(task), null);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Unable to schedule Folia entity task", e); }
    }

    public static void runLaterOnEntity(Plugin plugin, Entity entity, Runnable task, long delay) {
        if (!folia()) { Bukkit.getScheduler().runTaskLater(plugin, task, delay); return; }
        try {
            Object scheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
            Method method = scheduler.getClass().getMethod("runDelayed", Plugin.class, Consumer.class, Runnable.class, long.class);
            method.invoke(scheduler, plugin, consumer(task), null, delay);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Unable to schedule Folia entity task", e); }
    }

    private static Consumer<Object> consumer(Runnable task) { return ignored -> task.run(); }
    private static void invokeGlobal(String name, Object... args) {
        try {
            Object scheduler = Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler").invoke(Bukkit.getServer());
            for (Method method : scheduler.getClass().getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == args.length) { method.invoke(scheduler, args); return; }
            }
            throw new NoSuchMethodException(name);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Unable to schedule Folia global task", e); }
    }
    private static boolean hasMethod(Class<?> type, String name) {
        try { type.getMethod(name); return true; } catch (NoSuchMethodException e) { return false; }
    }
}
