import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class JerryRat implements Runnable {
    public static final String SERVER_PORT = "8080";
    ServerSocket serverSocket;

    public JerryRat() throws IOException {
        serverSocket = new ServerSocket(Integer.parseInt(SERVER_PORT));
        serverSocket.setSoTimeout(100);
    }

    @Override
    public void run() {
        while (true){
            try (
                    Socket clientSocket = serverSocket.accept();
                    PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))
            ) {
                // 截取路径名 拼接路径
                StringBuilder path = new StringBuilder("res/webroot/");
                String[] fileName = in.readLine().split(" ");
                String pathName = String.valueOf(path.append(fileName[1]));

                // UTF-8解码
                pathName = URLDecoder.decode(pathName, StandardCharsets.UTF_8);
                File file = new File(pathName);

                // 判断是否为HTTP/1.0请求;
                boolean rev = fileName.length == 3 && fileName[2].toLowerCase().endsWith("1.0");

                // 文件数据长度，文件类型，最后修改时间
                long length = 0;
                String contentType = null;
                String lastModified = null;

                // 判断路径是文件还是目录
                if (file.isFile()){
                    length = file.length();
                    contentType = URLConnection.getFileNameMap().getContentTypeFor(pathName);
                    lastModified = getLastModified(pathName);
                }else if (file.isDirectory()){
                    pathName = pathName +"/index.html";
                    file = new File(pathName);
                    length = file.length();
                    contentType = URLConnection.getFileNameMap().getContentTypeFor(pathName);
                    lastModified = getLastModified(pathName);
                }

                // Content-Type头getContentTypeFor方法的补丁,其方法无法正确识别.jss和.css文件
                if (pathName.toLowerCase().endsWith(".js")){
                    contentType = "application/javascript";
                }else if (pathName.toLowerCase().endsWith(".css")){
                    contentType = "text/css";
                }

                //分别处理请求
                if (fileName[0].equalsIgnoreCase("get")){
                    // 分别处理1.0和0.9
                    if (rev){
                        // 是否为user-agent特殊接口
                        switch (fileName[1]) {
                            case "/endpoints/user-agent":
                                userAgent(in, out);
                                break;
                            // 是否为redirect特殊接口
                            case "/endpoints/redirect":
                                in.readLine();
                                pathName = "http://localhost/";
                                movedPermanently(out, pathName);
                                break;
                            case "/secret.txt":
                                authorization(out ,in, pathName);
                                break;
                            default:
                                if (!in.readLine().equals("")) {
                                    badRequest(out);
                                    continue;
                                }
                                // 是否404
                                if (!new File(pathName).exists()) {
                                    notFound(out);
                                    continue;
                                }
                                printHead(out, length, contentType, lastModified);
                                break;
                        }
                    }else if (fileName.length == 2){
                        // 是否404
                        if (!new File(pathName).exists()){
                            out.print("404 Not Found" + "\r\n");
                            out.flush();
                            continue;
                        }
                        if (fileName[1].equals("/secret.txt")){
                            out.print("HTTP/1.0 401 Unauthorized" + "\r\n");
                            out.print("WWW-Authenticate: Basic realm=\"adalab\"" + "\r\n");
                            out.print("\r\n");
                            out.flush();
                            continue;
                        }
                        requestGet(file, out);
                    }else {
                        in.readLine();
                        badRequest(out);
                    }
                }else if (fileName[0].equalsIgnoreCase("head") && rev){
                    in.readLine();
                    // 是否404
                    if (!new File(pathName).exists()){
                        notFound(out);
                        continue;
                    }
                    printHead(out, length, contentType, lastModified);
                }else if (fileName[0].equalsIgnoreCase("post") && rev){
                    requestPost(fileName, pathName, out, in);
                }else if (!fileName[2].toLowerCase().endsWith("1.0") && fileName.length == 3){
                    badRequest(out);
                    return;
                }
                else {
                    methodNotAllowed(out);
                }
            } catch (IOException e) {
                System.err.println("TCP连接错误！");
            }
        }
    }

    public static void main(String[] args) throws IOException{
        JerryRat jerryRat = new JerryRat();
        jerryRat.run();
    }

    // 打印所有头
    public void printHead(PrintWriter output, long length, String contentType, String lastModified){
        DateFormat gmtDateFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z ", Locale.ENGLISH);
        String dateStr = gmtDateFormat.format(new Date());
        if (contentType == null){
            contentType = "text/plain";
        }

        output.print("HTTP/1.0 200 OK" + "\r\n");
        output.print("Date: " + dateStr + "\r\n");
        output.print("Server: Java HTTP Server from ADA" + "\r\n");
        output.print("Content-Length: " + length + "\r\n");
        output.print("Content-Type: " + contentType + "\r\n");
        output.print("Last-Modified: " + lastModified + "\r\n");
        output.print("\r\n");
        output.flush();
    }

    // 获得Last-Modified头
    public String getLastModified(String fileUrl){
        long timeStamp = new File(fileUrl).lastModified();

        DateFormat gmtDateFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z ", Locale.ENGLISH);

        return gmtDateFormat.format(new Date(timeStamp));
    }

    // GET请求
    public void requestGet(File file, PrintWriter out){
        FileInputStream fis;
        try {
            fis = new FileInputStream(file);
            // 设置一个，每次 装载信息的容器
            byte[] buf = new byte[1024];
            // 定义一个String用来存放字符串
            String str;
            // 开始读取数据
            int len;// 每次读取到的数据的长度
            while ((len = fis.read(buf)) != -1) {// len值为-1时，表示没有数据了
                str = new String(buf, 0, len, StandardCharsets.UTF_8);

                out.print(str);
                out.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // POST请求
    public void requestPost(String[] fileName, String pathName, PrintWriter out, BufferedReader in) throws IOException{
        if (fileName[1].toLowerCase().startsWith("/emails")){
            File file = new File(pathName);

            long length;

            // 是否输入Content-Length:
            String[] contentLength = in.readLine().split(" ");
            if (contentLength[0].equals("Content-Length:") && contentLength.length == 2){
                length = Long.parseLong(contentLength[1]);
            }else{
                badRequest(out);
                return;
            }

            in.readLine();
            FileOutputStream outStream = new FileOutputStream(file);	//文件输出流用于将数据写入文件

            //读取数据，并将读取到的数据存储到数组中
            byte[] data = new byte[1024];
            int i = 0;
            int n = in.read();
            while(i != length){
                data[i] = (byte)n;
                i++;
                n = in.read();
            }

            //解析数据
            String s = new String(data,0,i);

            String content = s.length() > length ? s.substring(0, Math.toIntExact(length)) : s;
            outStream.write(s.getBytes());
            outStream.close();	//关闭文件输出流

            // 打印头
            String contentType = URLConnection.getFileNameMap().getContentTypeFor(pathName);
            String lastModified = getLastModified(pathName);
            printHead(out, length, contentType, lastModified);

            out.print(content);
            out.flush();

        // /endpoints/null接口
        }else if (fileName[1].equals("/endpoints/null")){
            in.readLine();
            out.print("HTTP/1.0 204 No Content" + "\r\n");
            out.print("\r\n");
            out.flush();
        }else{
            badRequest(out);
        }
    }

    // /endpoints/user-agent接口
    public void userAgent(BufferedReader in, PrintWriter out) throws IOException{
        DateFormat gmtDateFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z ", Locale.ENGLISH);
        String dateStr = gmtDateFormat.format(new Date());

        //User-Agent: 内容
        String userAgent = in.readLine().split(" ")[1];

        if (in.readLine() != null){
            printHead(out, userAgent.length(), null, dateStr);
        }

        out.print(userAgent);
        out.flush();
    }

    // 404状态
    public void notFound(PrintWriter out){
            out.print("HTTP/1.0 404 Not Found" + "\r\n");
            out.print("\r\n");
            out.flush();
    }

    // 400状态
    public void badRequest(PrintWriter out){
        out.print("HTTP/1.0 400 Bad Request" + "\r\n");
        out.print("\r\n");
        out.flush();
    }

    // 501状态
    public void methodNotAllowed(PrintWriter out){
        out.print("HTTP/1.0 501 Not Implemented" + "\r\n");
        out.print("\r\n");
        out.flush();
    }

    // 301状态
    public void movedPermanently(PrintWriter out, String pathName){
        out.print("HTTP/1.0 301 Moved Permanently" + "\r\n");
        out.print("Location: " + pathName);
        out.flush();
    }

    public void authorization(PrintWriter out, BufferedReader in, String pathName) throws IOException{
        String[] str = in.readLine().split(" ");
        if (str[0].equals("") || !str[2].equals("aGVsbG86d29ybGQ=")){
            out.print("HTTP/1.0 401 Unauthorized" + "\r\n");
            out.print("WWW-Authenticate: Basic realm=\"adalab\"" + "\r\n");
            out.print("\r\n");
            out.flush();
        }else if (str[0].equals("Authorization:") && str[1].equals("Basic")){
            in.readLine();
            File file = new File(pathName);
            long length = file.length();
            String contentType = URLConnection.getFileNameMap().getContentTypeFor(pathName);
            String lastModified = getLastModified(pathName);
            printHead(out, length, contentType, lastModified);
            requestGet(file, out);
        }
    }
}