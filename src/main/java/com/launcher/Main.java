package com.launcher;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.google.gson.*;

public class Main {

    private static final String MC_VERSION = "MODPACK";
    private static final String LAUNCHER_DIR = System.getProperty("os.name").toLowerCase().contains("win")
            ? System.getenv("APPDATA") + "\\winterfell-launcher"
            : System.getProperty("user.home") + "/.winterfell-launcher";

    private static final String[] GAME_DIRS = {
        "mods", "config", "resourcepacks", "shaderpacks", "data",
        "journeymap", "server-resource-packs", "natives"
    };
    private static final String[] GAME_FILES = {
        "options.txt", "optionsshaders.txt", "servers.dat"
    };

    private static Path gameDir;
    private static Path librariesDir;
    private static Path assetsDir;
    private static Path nativesDir;

    private static final long MIN_RAM_MB = 4096;
    private static final long MIN_DISK_MB = 1024;
    private static final int REQUIRED_JAVA_VERSION = 21;
    private static final String ADOPTIUM_API = "https://api.adoptium.net/v3/binary/latest/"
            + REQUIRED_JAVA_VERSION + "/ga/%s/%s/jre/hotspot/normal/eclipse";

    public static void main(String[] args) {
        gameDir = Paths.get(LAUNCHER_DIR);
        librariesDir = gameDir.resolve("libraries");
        assetsDir = gameDir.resolve("assets");
        nativesDir = gameDir.resolve("natives");

        SwingUtilities.invokeLater(() -> {
            String error = checkRequirements();
            if (error != null) {
                JOptionPane.showMessageDialog(null, error, "Requisitos não atendidos", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
            createUI();
        });
    }

    private static String checkRequirements() {
        List<String> warnings = new ArrayList<>();

        // RAM - apenas alerta
        long totalRamMB = ((com.sun.management.OperatingSystemMXBean)
                java.lang.management.ManagementFactory.getOperatingSystemMXBean())
                .getTotalMemorySize() / (1024 * 1024);
        if (totalRamMB < MIN_RAM_MB) {
            warnings.add("RAM baixa: " + totalRamMB + "MB (recomendado: " + MIN_RAM_MB + "MB).\nO jogo pode ficar lento.");
        }

        // Disco - apenas alerta
        try {
            Path home = Paths.get(System.getProperty("user.home"));
            long freeSpaceMB = Files.getFileStore(home).getUsableSpace() / (1024 * 1024);
            if (freeSpaceMB < MIN_DISK_MB) {
                warnings.add("Espaço em disco baixo: " + freeSpaceMB + "MB livre (recomendado: " + MIN_DISK_MB + "MB).");
            }
        } catch (IOException ignored) {}

        if (!warnings.isEmpty()) {
            JOptionPane.showMessageDialog(null, String.join("\n\n", warnings),
                    "Aviso", JOptionPane.WARNING_MESSAGE);
        }
        return null;
    }


    private static void createUI() {
        JFrame frame = new JFrame("Winterfell Cousins Launcher");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(520, 400);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);

        // Icone da janela e taskbar
        try {
            BufferedImage icon = ImageIO.read(Main.class.getResourceAsStream("/icon.png"));
            frame.setIconImage(icon);
        } catch (Exception ignored) {}

        // Background GIF animado
        ImageIcon bgIcon = new ImageIcon(Main.class.getResource("/fantasy-background.gif"));
        JLabel bgLabel = new JLabel(bgIcon);
        bgLabel.setLayout(new GridBagLayout());
        frame.setContentPane(bgLabel);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);

        // Logo do Minecraft
        try {
            BufferedImage logoImg = ImageIO.read(Main.class.getResourceAsStream("/minecraft_logo.png"));
            Image scaled = logoImg.getScaledInstance(300, 50, Image.SCALE_SMOOTH);
            JLabel logoLabel = new JLabel(new ImageIcon(scaled));
            gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
            panel.add(logoLabel, gbc);
        } catch (Exception ignored) {}

        JLabel nickLabel = new JLabel("Nickname:");
        nickLabel.setForeground(Color.WHITE);
        nickLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        nickLabel.setOpaque(true);
        nickLabel.setBackground(new Color(0, 0, 0, 150));
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        panel.add(nickLabel, gbc);

        JTextField nickField = new JTextField(20);
        nickField.setPreferredSize(new Dimension(250, 32));
        nickField.setBackground(new Color(55, 55, 55));
        nickField.setForeground(Color.WHITE);
        nickField.setCaretColor(Color.WHITE);
        nickField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(80, 80, 80)),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        gbc.gridx = 1; gbc.gridy = 1;
        panel.add(nickField, gbc);

        JCheckBox rememberCheck = new JCheckBox("Lembrar nickname");
        rememberCheck.setForeground(Color.WHITE);
        rememberCheck.setOpaque(true);
        rememberCheck.setBackground(new Color(0, 0, 0, 150));
        rememberCheck.setFont(new Font("SansSerif", Font.PLAIN, 11));
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1;
        panel.add(rememberCheck, gbc);

        JCheckBox resetCheck = new JCheckBox("Redefinir ao iniciar");
        resetCheck.setForeground(Color.WHITE);
        resetCheck.setOpaque(true);
        resetCheck.setBackground(new Color(0, 0, 0, 150));
        resetCheck.setFont(new Font("SansSerif", Font.PLAIN, 11));
        gbc.gridx = 1; gbc.gridy = 2; gbc.gridwidth = 1;
        panel.add(resetCheck, gbc);

        // Carregar nick salvo
        Path nickFile = Paths.get(LAUNCHER_DIR, ".saved_nick");
        try {
            if (Files.exists(nickFile)) {
                String saved = Files.readString(nickFile).trim();
                if (!saved.isEmpty()) {
                    nickField.setText(saved);
                    rememberCheck.setSelected(true);
                }
            }
        } catch (IOException ignored) {}

        JButton playButton = new JButton("JOGAR");
        playButton.setBackground(new Color(56, 142, 60));
        playButton.setForeground(Color.WHITE);
        playButton.setFocusPainted(false);
        playButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        playButton.setPreferredSize(new Dimension(150, 40));
        playButton.setBorder(BorderFactory.createEmptyBorder());
        playButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 1;
        panel.add(playButton, gbc);

        JButton filesButton = new JButton("ARQUIVOS DO JOGO");
        filesButton.setBackground(new Color(48, 63, 159));
        filesButton.setForeground(Color.WHITE);
        filesButton.setFocusPainted(false);
        filesButton.setFont(new Font("SansSerif", Font.BOLD, 11));
        filesButton.setPreferredSize(new Dimension(180, 40));
        filesButton.setBorder(BorderFactory.createEmptyBorder());
        filesButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        filesButton.setEnabled(Files.exists(gameDir));
        gbc.gridx = 1; gbc.gridy = 3; gbc.gridwidth = 1;
        panel.add(filesButton, gbc);

        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(400, 22));
        progressBar.setBackground(new Color(50, 50, 50));
        progressBar.setForeground(new Color(76, 175, 80));
        progressBar.setVisible(false);
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        panel.add(progressBar, gbc);

        JLabel statusLabel = new JLabel(" ");
        statusLabel.setForeground(Color.WHITE);
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        statusLabel.setOpaque(true);
        statusLabel.setBackground(new Color(0, 0, 0, 150));
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        panel.add(statusLabel, gbc);

        JLabel signatureLabel = new JLabel("Made by: koenomatachisan");
        signatureLabel.setForeground(Color.WHITE);
        signatureLabel.setFont(new Font("SansSerif", Font.ITALIC, 10));
        signatureLabel.setOpaque(true);
        signatureLabel.setBackground(new Color(0, 0, 0, 150));
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.SOUTHEAST;
        panel.add(signatureLabel, gbc);

        filesButton.addActionListener((ActionEvent e) -> {
            try {
                Desktop.getDesktop().open(gameDir.toFile());
            } catch (IOException ex) {
                statusLabel.setText("Erro ao abrir pasta: " + ex.getMessage());
            }
        });

        playButton.addActionListener((ActionEvent e) -> {
            String nick = nickField.getText().trim();
            if (nick.isEmpty()) {
                statusLabel.setText("Insira um nickname!");
                return;
            }
            // Salvar ou remover nick
            try {
                Files.createDirectories(gameDir);
                if (rememberCheck.isSelected()) {
                    Files.writeString(nickFile, nick);
                } else {
                    Files.deleteIfExists(nickFile);
                }
            } catch (IOException ignored) {}

            boolean reset = resetCheck.isSelected();
            playButton.setEnabled(false);
            progressBar.setVisible(true);
            progressBar.setValue(0);
            statusLabel.setText("Preparando...");
            new Thread(() -> {
                try {
                    if (reset) {
                        updateStatus(statusLabel, "Redefinindo arquivos do jogo...");
                        deleteGameFiles();
                    }
                    launch(nick, statusLabel, progressBar);
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Erro: " + ex.getMessage());
                        playButton.setEnabled(true);
                        progressBar.setVisible(false);
                    });
                    ex.printStackTrace();
                }
            }).start();
        });

        bgLabel.add(panel);
        frame.setVisible(true);
    }

    private static void launch(String nickname, JLabel statusLabel, JProgressBar progressBar) throws Exception {
        Files.createDirectories(gameDir);
        Files.createDirectories(librariesDir);
        Files.createDirectories(assetsDir.resolve("indexes"));
        Files.createDirectories(assetsDir.resolve("objects"));
        Files.createDirectories(nativesDir);

        // Garantir Java 21+ disponível
        String javaPath = ensureJava(statusLabel, progressBar);

        // Extrair arquivos do modpack embutidos no launcher
        updateStatus(statusLabel, "Extraindo modpack...");
        extractResourceDir("modpack/mods", gameDir.resolve("mods"));
        extractResourceDir("modpack/resourcepacks", gameDir.resolve("resourcepacks"));
        extractResourceDir("modpack/shaderpacks", gameDir.resolve("shaderpacks"));
        extractResourceDir("modpack/config", gameDir.resolve("config"));
        extractResourceDir("modpack/data", gameDir.resolve("data"));
        extractResourceDir("modpack/journeymap", gameDir.resolve("journeymap"));
        extractResourceDir("modpack/server-resource-packs", gameDir.resolve("server-resource-packs"));
        String nativesResource = isWindows() ? "modpack/natives-windows" : "modpack/natives";
        extractResourceDir(nativesResource, nativesDir);

        for (String file : GAME_FILES) {
            Path dest = gameDir.resolve(file);
            if (!Files.exists(dest)) {
                extractResourceFile("modpack/" + file, dest);
            }
        }
        updateProgress(progressBar, 10);

        // Ler version json embutido
        updateStatus(statusLabel, "Lendo version json...");
        JsonObject versionJson;
        try (InputStream is = Main.class.getResourceAsStream("/modpack/MODPACK.json");
             Reader reader = new InputStreamReader(is)) {
            versionJson = JsonParser.parseReader(reader).getAsJsonObject();
        }

        // Baixar client jar
        Path clientJar = gameDir.resolve("client.jar");
        if (!Files.exists(clientJar)) {
            updateStatus(statusLabel, "Baixando client jar...");
            JsonObject clientDl = versionJson.getAsJsonObject("downloads").getAsJsonObject("client");
            String clientUrl = clientDl.get("url").getAsString();
            long clientSize = clientDl.get("size").getAsLong();
            downloadFileWithProgress(clientUrl, clientJar, clientSize, progressBar, 10, 40);
        }
        updateProgress(progressBar, 40);

        // Baixar/extrair libraries
        updateStatus(statusLabel, "Verificando libraries...");
        List<String> classpathEntries = new ArrayList<>();
        JsonArray libraries = versionJson.getAsJsonArray("libraries");
        int totalLibs = libraries.size();
        int libIndex = 0;

        for (JsonElement lib : libraries) {
            JsonObject libObj = lib.getAsJsonObject();

            if (libObj.has("rules") && !isAllowedByRules(libObj.getAsJsonArray("rules"))) {
                libIndex++;
                continue;
            }

            if (libObj.has("artifact")) {
                JsonObject artifact = libObj.getAsJsonObject("artifact");
                String path = artifact.get("path").getAsString();
                String url = artifact.get("url").getAsString();
                Path libPath = librariesDir.resolve(path);
                if (!Files.exists(libPath)) {
                    Files.createDirectories(libPath.getParent());
                    updateStatus(statusLabel, "Baixando: " + libObj.get("name").getAsString());
                    downloadFile(url, libPath);
                }
                classpathEntries.add(libPath.toAbsolutePath().toString());
            } else {
                // Fabric libs embutidas no launcher
                String name = libObj.get("name").getAsString();
                Path destPath = resolveLibPath(librariesDir, name);
                if (destPath != null) {
                    if (!Files.exists(destPath)) {
                        String resourcePath = resolveEmbeddedFabricLib(name);
                        if (resourcePath != null) {
                            Files.createDirectories(destPath.getParent());
                            extractResourceFile(resourcePath, destPath);
                        }
                    }
                    if (Files.exists(destPath)) {
                        classpathEntries.add(destPath.toAbsolutePath().toString());
                    }
                }
            }

            libIndex++;
            int progress = 40 + (int) ((libIndex / (double) totalLibs) * 30);
            updateProgress(progressBar, progress);
        }
        classpathEntries.add(clientJar.toAbsolutePath().toString());
        updateProgress(progressBar, 70);

        // Verificar assets
        updateStatus(statusLabel, "Verificando assets...");
        JsonObject assetIndex = versionJson.getAsJsonObject("assetIndex");
        String assetIndexId = assetIndex.get("id").getAsString();
        String assetIndexUrl = assetIndex.get("url").getAsString();
        Path assetIndexPath = assetsDir.resolve("indexes").resolve(assetIndexId + ".json");
        if (!Files.exists(assetIndexPath)) {
            Files.createDirectories(assetIndexPath.getParent());
            downloadFile(assetIndexUrl, assetIndexPath);
        }

        // Baixar asset objects
        updateStatus(statusLabel, "Baixando assets...");
        JsonObject assetIndexJson;
        try (Reader assetReader = Files.newBufferedReader(assetIndexPath)) {
            assetIndexJson = JsonParser.parseReader(assetReader).getAsJsonObject();
        }
        JsonObject objects = assetIndexJson.getAsJsonObject("objects");
        int totalAssets = objects.size();
        int assetCount = 0;
        for (Map.Entry<String, JsonElement> entry : objects.entrySet()) {
            String hash = entry.getValue().getAsJsonObject().get("hash").getAsString();
            String prefix = hash.substring(0, 2);
            Path objectPath = assetsDir.resolve("objects").resolve(prefix).resolve(hash);
            if (!Files.exists(objectPath)) {
                Files.createDirectories(objectPath.getParent());
                String objectUrl = "https://resources.download.minecraft.net/" + prefix + "/" + hash;
                downloadFile(objectUrl, objectPath);
            }
            assetCount++;
            if (assetCount % 50 == 0) {
                int progress = 70 + (int) ((assetCount / (double) totalAssets) * 25);
                updateProgress(progressBar, Math.min(progress, 95));
                updateStatus(statusLabel, "Baixando assets... (" + assetCount + "/" + totalAssets + ")");
            }
        }
        updateProgress(progressBar, 95);

        updateProgress(progressBar, 95);

        // Montar comando
        updateStatus(statusLabel, "Iniciando Minecraft...");
        String mainClass = versionJson.get("mainClass").getAsString();
        String classpath = String.join(File.pathSeparator, classpathEntries);

        List<String> command = new ArrayList<>();
        command.add(javaPath);
        command.add("-Xmx2G");
        command.add("-Xms512M");
        command.add("-Djava.library.path=" + nativesDir.toAbsolutePath());
        command.add("-Dminecraft.api.auth.host=http://0.0.0.0");
        command.add("-Dminecraft.api.account.host=http://0.0.0.0");
        command.add("-Dminecraft.api.session.host=http://0.0.0.0");
        command.add("-Dminecraft.api.services.host=http://0.0.0.0");
        command.add("-cp");
        command.add(classpath);
        command.add(mainClass);

        String uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + nickname).getBytes()).toString();
        command.add("--username"); command.add(nickname);
        command.add("--version"); command.add(MC_VERSION);
        command.add("--gameDir"); command.add(gameDir.toAbsolutePath().toString());
        command.add("--assetsDir"); command.add(assetsDir.toAbsolutePath().toString());
        command.add("--assetIndex"); command.add(assetIndexId);
        command.add("--uuid"); command.add(uuid);
        command.add("--accessToken"); command.add("0");
        command.add("--userType"); command.add("legacy");
        command.add("--quickPlayMultiplayer"); command.add("2.25.151.5:25565");

        updateProgress(progressBar, 100);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(gameDir.toFile());
        pb.inheritIO();
        Process process = pb.start();

        updateStatus(statusLabel, "Minecraft iniciado!");
        SwingUtilities.invokeLater(() -> {
            for (Window w : Window.getWindows()) w.setVisible(false);
        });
        process.waitFor();
        SwingUtilities.invokeLater(() -> {
            for (Window w : Window.getWindows()) w.setVisible(true);
            statusLabel.setText(" ");
            progressBar.setVisible(false);
        });
    }

    private static void deleteGameFiles() throws IOException {
        // Apaga tudo exceto o arquivo de nick salvo
        if (!Files.exists(gameDir)) return;
        Path nickFile = gameDir.resolve(".saved_nick");
        String savedNick = null;
        if (Files.exists(nickFile)) {
            savedNick = Files.readString(nickFile);
        }
        Files.walkFileTree(gameDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!file.equals(gameDir.resolve(".saved_nick"))) {
                    Files.delete(file);
                }
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (!dir.equals(gameDir)) {
                    try { Files.delete(dir); } catch (DirectoryNotEmptyException ignored) {}
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void extractResourceDir(String resourceDir, Path destDir) throws IOException {
        Files.createDirectories(destDir);
        URL url = Main.class.getResource("/" + resourceDir);
        if (url == null) return;

        if (url.getProtocol().equals("jar")) {
            String jarPath = java.net.URLDecoder.decode(url.getPath().substring(5, url.getPath().indexOf("!")), "UTF-8");
            try (JarFile jar = new JarFile(jarPath)) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String entryName = entry.getName();
                    if (entryName.startsWith(resourceDir + "/") && !entry.isDirectory()) {
                        String relativePath = entryName.substring(resourceDir.length() + 1);
                        Path dest = destDir.resolve(relativePath);
                        if (!Files.exists(dest)) {
                            Files.createDirectories(dest.getParent());
                            try (InputStream is = jar.getInputStream(entry)) {
                                Files.copy(is, dest);
                            }
                        }
                    }
                }
            }
        } else {
            // Running from IDE (file system)
            Path srcPath;
            try { srcPath = Paths.get(url.toURI()); } catch (URISyntaxException ex) { return; }
            Files.walkFileTree(srcPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Files.createDirectories(destDir.resolve(srcPath.relativize(dir)));
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path target = destDir.resolve(srcPath.relativize(file));
                    if (!Files.exists(target)) {
                        Files.copy(file, target);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private static void extractResourceFile(String resourcePath, Path dest) throws IOException {
        try (InputStream is = Main.class.getResourceAsStream("/" + resourcePath)) {
            if (is != null) {
                Files.createDirectories(dest.getParent());
                Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static String resolveEmbeddedFabricLib(String mavenName) {
        String[] parts = mavenName.split(":");
        if (parts.length < 3) return null;
        String artifact = parts[1];
        String version = parts[2];
        String fileName = artifact + "-" + version + ".jar";

        // Tentar encontrar no fabric-libs embutido
        if (mavenName.startsWith("net.fabricmc:")) {
            return "modpack/fabric-libs/" + artifact + "/" + version + "/" + fileName;
        } else if (mavenName.startsWith("org.ow2.asm:")) {
            return "modpack/fabric-libs/org/ow2/asm/" + artifact + "-" + version + "/" + fileName;
        }
        return null;
    }

    private static boolean isAllowedByRules(JsonArray rules) {
        String osName = System.getProperty("os.name").toLowerCase();
        String currentOs;
        if (osName.contains("win")) currentOs = "windows";
        else if (osName.contains("mac")) currentOs = "osx";
        else currentOs = "linux";

        for (JsonElement rule : rules) {
            JsonObject ruleObj = rule.getAsJsonObject();
            String action = ruleObj.get("action").getAsString();
            if (ruleObj.has("os")) {
                String ruleOs = ruleObj.getAsJsonObject("os").get("name").getAsString();
                if (action.equals("allow") && ruleOs.equals(currentOs)) return true;
                if (action.equals("disallow") && ruleOs.equals(currentOs)) return false;
            }
        }
        return false;
    }

    private static Path resolveLibPath(Path baseDir, String mavenName) {
        String[] parts = mavenName.split(":");
        if (parts.length < 3) return null;
        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String fileName = artifact + "-" + version + ".jar";
        return baseDir.resolve(group).resolve(artifact).resolve(version).resolve(fileName);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private static String ensureJava(JLabel statusLabel, JProgressBar progressBar) throws Exception {
        // 1. Verificar JRE já baixado pelo launcher
        Path jreDir = gameDir.resolve("jre");
        String javaBin = isWindows() ? "bin/java.exe" : "bin/java";
        if (Files.isDirectory(jreDir)) {
            try (var dirs = Files.list(jreDir)) {
                Optional<Path> existing = dirs
                        .map(p -> p.resolve(javaBin))
                        .filter(Files::isExecutable)
                        .findFirst();
                if (existing.isPresent()) return existing.get().toString();
            }
        }

        // 2. Verificar sistema (JAVA_HOME e /usr/lib/jvm)
        String systemJava = findSystemJava21();
        if (systemJava != null) return systemJava;

        // 3. Baixar JRE Adoptium Temurin 21
        updateStatus(statusLabel, "Baixando Java 21 (primeira vez)...");
        Files.createDirectories(jreDir);

        String os = isWindows() ? "windows" : "linux";
        String arch = System.getProperty("os.arch").contains("aarch64") ? "aarch64" : "x64";
        String url = String.format(ADOPTIUM_API, os, arch);

        String ext = isWindows() ? ".zip" : ".tar.gz";
        Path archive = jreDir.resolve("jre-download" + ext);
        downloadFileWithProgress(url, archive, -1, progressBar, 0, 5);

        // Extrair
        updateStatus(statusLabel, "Extraindo Java 21...");
        if (isWindows()) {
            extractZip(archive, jreDir);
        } else {
            ProcessBuilder pb = new ProcessBuilder("tar", "xzf", archive.toString(), "-C", jreDir.toString());
            pb.start().waitFor();
        }
        Files.deleteIfExists(archive);

        // Encontrar o java extraído
        try (var dirs = Files.list(jreDir)) {
            Optional<Path> extracted = dirs
                    .map(p -> p.resolve(javaBin))
                    .filter(Files::isExecutable)
                    .findFirst();
            if (extracted.isPresent()) return extracted.get().toString();
        }
        throw new RuntimeException("Falha ao instalar Java 21. Instale manualmente: sudo apt install openjdk-21-jdk");
    }

    private static String findSystemJava21() {
        // JAVA_HOME
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null) {
            Path bin = Paths.get(javaHome, "bin", "java");
            if (Files.isExecutable(bin) && getJavaVersion(bin.toString()) >= REQUIRED_JAVA_VERSION) {
                return bin.toString();
            }
        }
        // Linux: /usr/lib/jvm
        if (!isWindows()) {
            try {
                Path jvmDir = Paths.get("/usr/lib/jvm");
                if (Files.isDirectory(jvmDir)) {
                    try (var dirs = Files.list(jvmDir)) {
                        Optional<Path> found = dirs
                                .map(p -> p.resolve("bin/java"))
                                .filter(Files::isExecutable)
                                .filter(p -> getJavaVersion(p.toString()) >= REQUIRED_JAVA_VERSION)
                                .max(Comparator.comparingInt(p -> getJavaVersion(p.toString())));
                        if (found.isPresent()) return found.get().toString();
                    }
                }
            } catch (IOException ignored) {}
        }
        // Default java no PATH
        if (getJavaVersion("java") >= REQUIRED_JAVA_VERSION) return "java";
        return null;
    }

    private static int getJavaVersion(String javaPath) {
        try {
            Process p = new ProcessBuilder(javaPath, "-version").redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes());
            p.waitFor();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"(\\d+)([.\\d]*)\"")
                    .matcher(output);
            if (m.find()) return Integer.parseInt(m.group(1));
        } catch (Exception ignored) {}
        return 0;
    }

    private static void extractZip(Path zipFile, Path destDir) throws IOException {
        try (var zis = new java.util.zip.ZipInputStream(Files.newInputStream(zipFile))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path dest = destDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(zis, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void updateStatus(JLabel label, String text) {
        SwingUtilities.invokeLater(() -> label.setText(text));
    }

    private static void updateProgress(JProgressBar bar, int value) {
        SwingUtilities.invokeLater(() -> bar.setValue(value));
    }

    private static void downloadFile(String urlStr, Path dest) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        try (InputStream in = conn.getInputStream()) {
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void downloadFileWithProgress(String urlStr, Path dest, long totalSize, JProgressBar progressBar, int startPct, int endPct) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        try (InputStream in = conn.getInputStream(); OutputStream out = Files.newOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            long downloaded = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                downloaded += read;
                if (totalSize > 0) {
                    int pct = startPct + (int) ((downloaded * (endPct - startPct)) / totalSize);
                    updateProgress(progressBar, pct);
                }
            }
        }
    }
}
