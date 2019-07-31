package com.github.bpazy.socks5;

import com.github.bpazy.commons.io.IOUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author ziyuan
 * created on 2019/7/30
 */
@Slf4j
public class Socks5 {

    private static final int EOF = -1;
    private static final ExecutorService handshakeExecutor = Executors.newCachedThreadPool();
    private static final ExecutorService exchangeExecutor = Executors.newCachedThreadPool();

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(9999);
        while (true) {
            Socket clientSocket = serverSocket.accept();
            handshakeExecutor.execute(() -> {
                try {
                    Socket dstSocket = handshake(clientSocket);
                    transferData(clientSocket, dstSocket);
                } catch (IOException e) {
                    log.error("", e);
                }
            });
        }
    }

    private static Socket handshake(Socket clientSocket) throws IOException {
        InputStream inputStream = clientSocket.getInputStream();
        OutputStream outputStream = clientSocket.getOutputStream();
        int ver = inputStream.read();
        int nMethods = inputStream.read();
        byte[] methods = new byte[nMethods];
        int readLen = inputStream.read(methods);
        if (readLen == EOF) {
            clientSocket.close();
            throw new IOException();
        }
        log.debug("client handshake version: {}, nMethods: {}, methods: {}", ver, nMethods, methods);

        outputStream.write(new byte[]{5, 0});
        outputStream.flush();

        ver = inputStream.read();
        int cmd = inputStream.read();
        if (cmd != 1) {
            throw new RuntimeException("not support cmd: " + cmd);
        }
        int rsv = inputStream.read();
        int atyp = inputStream.read();

        int dstAddrLen;
        if (atyp == 1) {
            dstAddrLen = 4;
        } else if (atyp == 4) {
            dstAddrLen = 16;
        } else if (atyp == 3) {
            dstAddrLen = inputStream.read();
        } else {
            throw new RuntimeException("not support atyp: " + atyp);
        }

        byte[] dstAddrBytes = new byte[dstAddrLen];
        byte[] dstPortBytes = new byte[2];
        if (inputStream.read(dstAddrBytes) == EOF || inputStream.read(dstPortBytes) == EOF) {
            clientSocket.close();
            throw new IOException();
        }
        int dstPort = getPort(dstPortBytes);
        String dstAddr = atyp == 3 ? new String(dstAddrBytes) : InetAddress.getByAddress(dstAddrBytes).getHostAddress();
        log.debug("client request ver: {}, cmd: {}, rsv: {}, atyp: {}, dstAddr: {}, dstPort: {}", ver, cmd, rsv, atyp, dstAddrBytes, dstPortBytes);
        log.info("client request url: {}, port: {}", dstAddr, dstPort);

        int dynamicLen = atyp == 3 ? 1 : 0;
        byte[] domainResponseBytes = new byte[4 + dynamicLen + dstAddrBytes.length + 2]; // (ver, rep, rsv, aytp) + addrLen + addr + port

        // set handshake response ver, cmd, rsv, atyp
        domainResponseBytes[0] = 5;
        domainResponseBytes[1] = 0;
        domainResponseBytes[2] = 0;
        domainResponseBytes[3] = (byte) atyp;

        // set handshake response dst.addr
        if (atyp == 1 || atyp == 4) {
            System.arraycopy(dstAddrBytes, 0, domainResponseBytes, 4, dstAddrBytes.length);
        } else {
            domainResponseBytes[4] = (byte) dstAddrBytes.length;
            System.arraycopy(dstAddrBytes, 0, domainResponseBytes, 5, dstAddrBytes.length);
        }

        // set handshake response dst.port
        domainResponseBytes[domainResponseBytes.length - 2] = dstPortBytes[0];
        domainResponseBytes[domainResponseBytes.length - 1] = dstPortBytes[1];

        outputStream.write(domainResponseBytes);
        outputStream.flush();
        log.debug("handshake over");
        return new Socket(dstAddr, dstPort);
    }

    private static void transferData(Socket clientSocket, Socket dstSocket) {
        exchangeExecutor.execute(() -> {
            log.debug("transfer from client({}) to remote({}:{})", clientSocket.getInetAddress().getHostAddress(), dstSocket.getInetAddress().getHostAddress(), dstSocket.getPort());
            transfer(clientSocket, dstSocket);
            log.debug("transfer from client({}) to remote({}:{}) end", clientSocket.getInetAddress().getHostAddress(), dstSocket.getInetAddress().getHostAddress(), dstSocket.getPort());
        });
        exchangeExecutor.execute(() -> {
            log.debug("transfer from remote({}:{}) to client({})", dstSocket.getInetAddress().getHostAddress(), dstSocket.getPort(), clientSocket.getInetAddress().getHostAddress());
            transfer(dstSocket, clientSocket);
            log.debug("transfer from remote({}:{}) to client({}) end", dstSocket.getInetAddress().getHostAddress(), dstSocket.getPort(), clientSocket.getInetAddress().getHostAddress());
        });
    }

    private static void transfer(Socket from, Socket to) {
        try {
            long copiedLen = IOUtils.copy(from.getInputStream(), to.getOutputStream());
            if (copiedLen == 0 && (!to.isClosed() || !from.isClosed())) {
                throw new IOException();
            }
            to.getOutputStream().flush();
        } catch (IOException ignore) {
        } finally {
            try {
                from.close();
                to.close();
            } catch (IOException ignore) {
            }
        }
    }

    private static int getPort(byte[] dstPort) {
        return dstPort[1] > 0 ? dstPort[0] * 256 + dstPort[1] : dstPort[0] * 256 + 256 + dstPort[1];
    }
}
