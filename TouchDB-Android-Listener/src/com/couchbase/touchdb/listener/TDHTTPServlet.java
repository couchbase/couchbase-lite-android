package com.couchbase.touchdb.listener;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.couchbase.touchdb.TDBody;
import com.couchbase.touchdb.TDServer;
import com.couchbase.touchdb.router.TDRouter;
import com.couchbase.touchdb.router.TDURLConnection;

@SuppressWarnings("serial")
public class TDHTTPServlet extends HttpServlet {

    private TDServer server;

    public void setServer(TDServer server) {
        this.server = server;
    }

    @Override
    public void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {


        synchronized (server) {

            //set path
            URL url = new URL("touchdb://" + request.getRequestURI());
            TDURLConnection conn = (TDURLConnection)url.openConnection();
            conn.setDoOutput(true);

            //set the method
            conn.setRequestMethod(request.getMethod());

            //set the headers
            Enumeration<String> headerNames = request.getHeaderNames();
            while(headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                conn.setRequestProperty(headerName, request.getHeader(headerName));
            }

            //set the body
            InputStream is = request.getInputStream();
            //fixme dont think i should have to call available here
            //but its blocking on get requests otherwise
            if(is != null && is.available() > 0) {
                conn.setDoInput(true);
                OutputStream os = conn.getOutputStream();
                byte[] buffer = new byte[1024];
                int lenRead = is.read(buffer, 0, 1024);
                while(lenRead > 0) {
                    os.write(buffer, 0, lenRead);
                    lenRead = is.read(buffer, 0, 1024);
                }
                is.close();
                os.close();
            }

            TDRouter router = new TDRouter(server, conn);
            router.start();

            //set the response code
            response.setStatus(conn.getResponseCode());

            //add the resonse headers
            Map<String, List<String>> headers = conn.getHeaderFields();
            if(headers != null) {
                for (String headerName : headers.keySet()) {
                    for (String headerValue : headers.get(headerName)) {
                        response.addHeader(headerName, headerValue);
                    }
                }
            }

            //write the response body
            OutputStream os = response.getOutputStream();
            TDBody body = conn.getResponseBody();
            if(body != null) {
                byte[] json = body.getJson();
                os.write(json);
            }
            os.close();
        }

    }

}
