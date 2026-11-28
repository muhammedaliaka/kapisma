package tr.kapisma.ultra.bridge;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

/** Fixed operation, authenticated loopback-only bridge; launched by authorized ADB shell. */
public final class Bridge {
  private static final String ROOT="/data/local/tmp/kapisma-ultimate-bridge";
  private static final int PORT=39761;
  static String dailyToken,serverToken;
  static String line(InputStream in,int max)throws IOException {
    ByteArrayOutputStream b=new ByteArrayOutputStream();
    for(int i=0;i<=max;i++){int c=in.read();if(c==-1||c==10)return new String(b.toByteArray(),StandardCharsets.UTF_8);if(c==13)continue;b.write(c);}
    throw new IOException("request too long");
  }
  static String call(String filename,String arg)throws Exception{
    Process p=new ProcessBuilder("/system/bin/sh",ROOT+"/"+filename,arg).redirectErrorStream(true).start();
    ByteArrayOutputStream out=new ByteArrayOutputStream();
    Thread t=new Thread(()->{try(InputStream in=p.getInputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){synchronized(out){if(out.size()<65536)out.write(b,0,Math.min(n,65536-out.size()));}}}catch(IOException ignored){}});
    t.start();
    // Do not kill or retry a mutating shell on client disconnect. Its journal is recovery evidence.
    if(!p.waitFor(120,TimeUnit.SECONDS)){p.destroy();t.join(1500);throw new IOException("operation timed out; check status before retry");}
    t.join(1500);String s;
    synchronized(out){s=new String(out.toByteArray(),StandardCharsets.UTF_8);}
    if(p.exitValue()!=0)throw new IOException("exit="+p.exitValue()+"\n"+s);
    return s;
  }
  static String execute(String op)throws Exception{
    switch(op){
      case "DAILY":return call("daily.sh","--apply");
      case "STATUS":return call("server_mode.sh","--status");
      case "ENTER":
        String s=call("server_mode.sh","--enter");
        try{return s+"\n"+call("p1_cleanup.sh","--apply")+"\nserver_mode=active";}
        catch(Exception e){try{call("server_mode.sh","--leave");}catch(Exception restore){throw new IOException("cleanup failed; restoration incomplete; use status",restore);}throw new IOException("cleanup failed; original packages restored",e);}
      case "LEAVE":return call("server_mode.sh","--leave");
      case "PING":return "bridge=ready";
      default:throw new IOException("unsupported operation");
    }
  }
  public static void main(String[] args)throws Exception{
    dailyToken=new String(Files.readAllBytes(Paths.get(ROOT,"daily.token")),StandardCharsets.US_ASCII).trim();
    serverToken=new String(Files.readAllBytes(Paths.get(ROOT,"server.token")),StandardCharsets.US_ASCII).trim();
    if(!dailyToken.matches("[a-f0-9]{64}")||!serverToken.matches("[a-f0-9]{64}")||dailyToken.equals(serverToken))throw new IOException("invalid tokens");
    try(ServerSocket srv=new ServerSocket()){
      srv.setReuseAddress(false);srv.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),PORT),8);
      System.out.println("ULTRA bridge listening on loopback");
      while(true){
        try(Socket sock=srv.accept()){
          sock.setSoTimeout(5000);
          OutputStream out=sock.getOutputStream();
          String[] q=line(sock.getInputStream(),128).split(" ",-1);
          boolean valid=q.length==3&&q[0].equals("ULTRA1");
          boolean daily=valid&&MessageDigest.isEqual(dailyToken.getBytes(StandardCharsets.US_ASCII),q[1].getBytes(StandardCharsets.US_ASCII));
          boolean server=valid&&MessageDigest.isEqual(serverToken.getBytes(StandardCharsets.US_ASCII),q[1].getBytes(StandardCharsets.US_ASCII));
          if(!(daily||server)){
            out.write("ERR\nAuthentication failed\n".getBytes(StandardCharsets.UTF_8));out.flush();Thread.sleep(250);continue;
          }
          String result;
          if(daily&&!q[2].equals("DAILY")&&!q[2].equals("PING")||server&&q[2].equals("DAILY"))result="ERR\nOperation forbidden for this app";
          else try{result="OK\n"+execute(q[2]);}catch(Exception e){result="ERR\n"+e.getMessage();}
          out.write(result.getBytes(StandardCharsets.UTF_8));out.flush();
        }catch(IOException e){System.err.println("Client ended: "+e.getClass().getSimpleName());}
      }
    }
  }
}
