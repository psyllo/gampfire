import java.net.*

class Http {

    static request(method, urlStr, content, contentType) {
        def url = new URL(urlStr)
        def conn = url.openConnection()
        conn.setRequestMethod(method)
        conn.setDoInput(true)
        if(content) {
            conn.setDoOutput(true)
            if(contentType) {
                conn.setRequestProperty('Content-type', contentType)
            }
            def wr = new OutputStreamWriter(conn.getOutputStream())
            wr.write(content)
            wr.flush()
        }
        def rd = new BufferedReader(new InputStreamReader(conn.getInputStream()))
        def builder = new StringBuilder()
        def line
        while ((line = rd.readLine()) != null) {
            builder.append(line)
        }
        def result = [code: conn.responseCode, body: builder.toString()]
        conn.disconnect()
        result
    }

    static post(url, content, contentType) {
        request('POST', url, content, contentType)
    }

    static post(url) {
        request('POST', url, null, null)
    }

    static postXml(url, xml) {
        request('POST', url, xml, 'application/xml')
    }

    static get(url) {
        request('GET', url, null, null)
    }
}
