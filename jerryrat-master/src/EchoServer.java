import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class EchoServer implements Runnable {
    public static final String SERVER_PORT = "3456";
    ServerSocket serverSocket;
    int i = 1;

    public EchoServer() throws IOException {
        serverSocket = new ServerSocket(Integer.parseInt(SERVER_PORT));
    }

    @Override
    public void run() {
        try (
                Socket clientSocket = serverSocket.accept();
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))
        ) {
            String s;
            while ((s = in.readLine()) != null) {
                out.println(i + ". " + s);
                i++;
            }
        } catch (IOException e) {
            System.err.println("TCP连接错误！");
        }
    }

    public static void main(String[] args) throws IOException {
        EchoServer echoServer = new EchoServer();
        new Thread(echoServer).run();
    }
}