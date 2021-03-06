package dclient.installer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dclient.Key;

public class KeyEncrypt {
	private final int DEFAULT_PASSWORD_LENGHT = 64;
	private final String DEFAULT_ENV_VARIABLE = "DCLIENT";
	private final String DEFAULT_ENCRYPTION_ALGORITHM = "PBEWithHMACSHA512AndAES_256";
	private final String DEFAULT_PASSWORD_VALID_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789%*()_+-=[]";

	private int passwordLenght;
	private String passwordValidChars;
	private String installationFolder;

	Key key;

	private String userPassword;
	private String sshFileName;
	private String sshNameIdentifier;
	private String sshPassword;
	private String envName;
	private String envPassword;

	private String envAlgorithm;

	Process sshCreation;

	Properties config;

	Logger logger;

	public static void main(String[] args) {
		KeyEncrypt keyEncrypt = new KeyEncrypt();
		keyEncrypt.initLogFile();
		keyEncrypt.loadInitProperties();
		keyEncrypt.userInput();
		keyEncrypt.setEnvVar();
		keyEncrypt.initKey();
		keyEncrypt.nukeConfigDirectory();
		keyEncrypt.setSSH();
		keyEncrypt.movePublicKey();
		keyEncrypt.createPropertiesFile();
	}

	private void initLogFile() {
		boolean append = true;
		FileHandler handler = null;
		try {
			handler = new FileHandler("installation.log", append);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
		logger = Logger.getLogger("dclient");
		logger.addHandler(handler);
	}

	private void loadInitProperties() {
		config = new Properties();
		try {
			config.load(new FileInputStream("./installConfig.properties"));
		} catch (IOException e) {
			logger.severe("Got an exception. " + e.getMessage());
			e.printStackTrace();
			System.exit(-1);
		}

		passwordLenght = Integer
				.parseInt(config.getProperty("password.lenght", Integer.toString(DEFAULT_PASSWORD_LENGHT)));
		installationFolder = config.getProperty("installation.folder",
				System.getProperty("user.home") + "\\.dclient\\");
		sshFileName = config.getProperty("ssh.keyName", "id_dclient_rsa");
		sshNameIdentifier = config.getProperty("ssh.identifier", System.getProperty("user.name"));
		envName = config.getProperty("env.variable", DEFAULT_ENV_VARIABLE);
		envAlgorithm = config.getProperty("enc.algorithm", DEFAULT_ENCRYPTION_ALGORITHM);
		passwordValidChars = config.getProperty("password.validChars", DEFAULT_PASSWORD_VALID_CHARS);
	}

	private void userInput() {
		InputStreamReader isr = new InputStreamReader(System.in);
		BufferedReader br = new BufferedReader(isr);

		try {
			System.out.print("Inserisci la password => ");
			userPassword = br.readLine();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void initKey() {
		key = new Key(userPassword, envPassword, envAlgorithm);
		logger.info("User password: " + userPassword + "\nEnv password: " + envPassword + "\nUsed algorythm: "
				+ envAlgorithm + "\n" + key.getEnc().toString());
	}

	private void nukeConfigDirectory() {
		try {
			Files.createDirectories(Paths.get(installationFolder));
			for (File sub : new File(installationFolder).listFiles())
				sub.delete();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void setEnvVar() {
		envPassword = passwordGenerator();
		try {
			// Detect if Windows is running and set ENV variable for current user
			if (System.getProperty("os.name").toLowerCase().contains("win")) {
				Runtime.getRuntime().exec("setx " + envName + " \"" + envPassword + "\"");
			}
			// Detect if a better OS is running and set ENV variable for current user
			else {
				// Detect common bash profile in home folder and add export for variable.
				Path testPath = Paths.get(System.getProperty("user.home"));
				Stream<Path> stream = Files.find(testPath, 1, (path, basicFileAttributes) -> {
					File file = path.toFile();
					return !file.isDirectory() && file.getName().substring(file.getName().length() - 4).equals("rc");
				});

				for (Path file : stream.collect(Collectors.toList())) {
					Runtime.getRuntime().exec("sed \"/" + envName + "/c\\export " + envName + " = " + envPassword + "\" " + file.toString() + 
					"&& grep -qF 'export " + envName + "' " + file.toString() + " || echo 'export " + envName + " = " + envPassword + "' >> " + file.toString());
				}

				stream.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		String logging = "Added or edited successfully the user environment variable" + envName + " with value "
				+ envPassword + "\n" + "The from the system i can read that the variable " + envName + " equals to "
				+ System.getenv(envName);
		logger.info(logging);
	}

	private void setSSH() {
		sshPassword = passwordGenerator();
		logger.info("Generated password for ssh:" + sshPassword);
		String command = ("ssh-keygen -f " + installationFolder + sshFileName + " -t rsa  -b 4096 -C "
				+ sshNameIdentifier + " -N \"" + sshPassword + "\"");

		ProcessBuilder sshCreationBuilder = new ProcessBuilder("cmd.exe", "/c", command);
		sshCreationBuilder.redirectOutput(Redirect.INHERIT);
		sshCreationBuilder.redirectError(Redirect.INHERIT);
		try {
			sshCreation = sshCreationBuilder.start();
			sshCreation.waitFor();
		} catch (IOException | InterruptedException e) {
			logger.severe("Got an exception. " + e.getMessage());
		}
	}

	private void movePublicKey() {
		try {
			Files.move(Paths.get(installationFolder + "\\" + sshFileName + ".pub"), Paths.get(
					".\\" + System.getProperty("user.name") + "-" + InetAddress.getLocalHost().getHostName() + ".pub"),
					StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private String passwordGenerator() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < passwordLenght; i++) {
			sb.append(passwordValidChars.charAt(ThreadLocalRandom.current().nextInt(0, passwordValidChars.length())));
		}
		return sb.toString();
	}

	private void createPropertiesFile() {
		Properties installProperties = new Properties();

		installProperties.setProperty("rsppTable.daysAdvance", config.getProperty("rsppTable.daysAdvance"));
		installProperties.setProperty("dateFormat", config.getProperty("dateFormat"));

		installProperties.setProperty("db.host", "localhost");
		installProperties.setProperty("db.port", config.getProperty("db.port", "5432"));
		installProperties.setProperty("db.user", "ENC(" + key.getEnc().encrypt(config.getProperty("db.user")) + ")");
		installProperties.setProperty("db.password",
				"ENC(" + key.getEnc().encrypt(config.getProperty("db.password")) + ")");
		installProperties.setProperty("db.database", config.getProperty("db.database"));

		installProperties.setProperty("ssh.host", config.getProperty("ssh.host"));
		installProperties.setProperty("ssh.user", "ENC(" + key.getEnc().encrypt(config.getProperty("ssh.user")) + ")");
		installProperties.setProperty("ssh.keyName", sshFileName);
		installProperties.setProperty("ssh.keyPassword", "ENC(" + key.getEnc().encrypt(sshPassword) + ")");
		installProperties.setProperty("ssh.port", config.getProperty("ssh.port", "22"));

		installProperties.setProperty("env.varName", config.getProperty("env.varName", DEFAULT_ENV_VARIABLE));
		installProperties.setProperty("enc.algorithm",
				config.getProperty("enc.algorithm", DEFAULT_ENCRYPTION_ALGORITHM));

		File file = new File(installationFolder + "/config.properties");
		FileOutputStream fileOut;
		try {
			fileOut = new FileOutputStream(file);
			installProperties.store(fileOut, "This is an autogenerated file for dclient");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
