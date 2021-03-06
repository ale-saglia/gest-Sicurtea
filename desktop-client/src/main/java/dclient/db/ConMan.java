package dclient.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import dclient.model.Model;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

public class ConMan {
	Properties config;
	Session session;
	private Connection tempConnection;

	public ConMan(Properties config) {
		this.config = config;
		this.session = getSSHConnection();
	}

	public Session getSSHConnection() {
		if(session == null || !session.isConnected())
			session = ConMan.getSSHSession(config);
		return session;
	}

	public String closeSSHConnection() {
		closeDBConnection();
		
		String message = null;
		try {
			message = "Closing: " + session.getPortForwardingL();
		} catch (JSchException e) {
			e.printStackTrace();
		}
		this.session.disconnect();
		return message;
	}
	
	public Connection getDBConnection() {
		try {
			if(tempConnection == null || tempConnection.isClosed())
				tempConnection = ConMan.getDBConnection(session, config);
			return tempConnection;
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public void closeDBConnection() {
		try {
			if(!tempConnection.isClosed())
				tempConnection.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	private static Session getSSHSession(Properties config) {
		JSch jsch = new JSch();
		Session session = null;
		String privateKeyPath = Model.getConfigPath() + config.getProperty("ssh.keyName");
		try {
			// SSH connection setup && port forwarding
			jsch.addIdentity(privateKeyPath, config.getProperty("ssh.keyPassword"));
			session = jsch.getSession(config.getProperty("ssh.user"), config.getProperty("ssh.host"),
					Integer.parseInt(config.getProperty("ssh.port")));
			session.setConfig("PreferredAuthentications", "publickey,keyboard-interactive,password");
	
			Properties sshConnConfig = new java.util.Properties();
			sshConnConfig.put("StrictHostKeyChecking", "no");
	
			session.setConfig(sshConnConfig);
			session.connect();
	
			System.out.println("Connected");
	
			System.out.println(session.getPortForwardingL());
	
			System.out
					.println("localhost:"
							+ session.setPortForwardingL(0, config.getProperty("ssh.host"),
									Integer.parseInt(config.getProperty("db.port")))
							+ " -> " + config.getProperty("db.port"));
			System.out.println("Port Forwarded");
	
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println(e.getClass().getName() + ": " + e.getMessage());
	
			final Alert alert = new Alert(AlertType.ERROR);
			alert.setTitle("Impossibile connettersi al database!");
			alert.setHeaderText("Non è stato possibile connettersi al database");
			alert.setContentText("Questo potrebbe essere causato da uno dei seguenti scenari:\n\r "
					+ "- La password non è stata inserita correttamente\n\r"
					+ "- Non è possibile connettersi al server\n\r"
					+ "- I dati nel file di configurazione non sono corretti"
					+ "- Se è appena stat eseguita l'installazione è necessaria la disconnessione o il riavvio");
			alert.showAndWait();
		}
		return session;
	}

	private static Connection getDBConnection(Session session, Properties config) {
		Connection conn = null;
	
		try {
			// DB connection
			Class.forName("org.postgresql.Driver");
			String dbString = "jdbc:postgresql://" + config.getProperty("db.host") + ":"
					+ session.setPortForwardingL(0, config.getProperty("db.host"), Integer.parseInt(config.getProperty("db.port"))) + "/" + config.getProperty("db.database");
	
			conn = DriverManager.getConnection(dbString, (config.getProperty("db.user")), (config.getProperty("db.password")));
	
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println(e.getClass().getName() + ": " + e.getMessage());
		}
		return conn;
	}
}
