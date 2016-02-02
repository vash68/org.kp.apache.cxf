/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.cxf.attachment;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.activation.DataHandler;

import org.apache.cxf.message.Attachment;
import org.apache.cxf.message.Message;

public class AttachmentSerializer {
    // http://tools.ietf.org/html/rfc2387
    private static final String DEFAULT_MULTIPART_TYPE = "multipart/related";
    
    private Message message;
    private String bodyBoundary;
    private OutputStream out;
    private String encoding;
    
    private String multipartType;
    private Map<String, List<String>> rootHeaders = Collections.emptyMap();
    private boolean xop = true;
    private boolean writeOptionalTypeParameters = true;
    
    public AttachmentSerializer(Message messageParam) {
        message = messageParam;
    }

    public AttachmentSerializer(Message messageParam, 
                                String multipartType,
                                boolean writeOptionalTypeParameters,
                                Map<String, List<String>> headers) {
        message = messageParam;
        this.multipartType = multipartType;
        this.writeOptionalTypeParameters = writeOptionalTypeParameters;
        this.rootHeaders = headers;
    }
    
    /**
     * Serialize the beginning of the attachment which includes the MIME 
     * beginning and headers for the root message.
     */
    public void writeProlog() throws IOException {
        // Create boundary for body
        bodyBoundary = AttachmentUtil.getUniqueBoundaryValue();

        String bodyCt = (String) message.get(Message.CONTENT_TYPE);
        String bodyCtParams = null;
        String bodyCtParamsEscaped = null;
        // split the bodyCt to its head that is the type and its properties so that we
        // can insert the values at the right places based on the soap version and the mtom option
        // bodyCt will be of the form
        // soap11 -> text/xml
        // soap12 -> application/soap+xml; action="urn:ihe:iti:2007:RetrieveDocumentSet"
        if (bodyCt.indexOf(';') != -1) {
            int pos = bodyCt.indexOf(';');
            // get everything from the semi-colon
            bodyCtParams = bodyCt.substring(pos);
            bodyCtParamsEscaped = escapeQuotes(bodyCtParams); 
            // keep the type/subtype part in bodyCt
            bodyCt = bodyCt.substring(0, pos);
        }
        // Set transport mime type
        String requestMimeType = multipartType == null ? DEFAULT_MULTIPART_TYPE : multipartType;
        
        StringBuilder ct = new StringBuilder();
        ct.append(requestMimeType);
        
        // having xop set to true implies multipart/related, but just in case...
        boolean xopOrMultipartRelated = xop 
            || DEFAULT_MULTIPART_TYPE.equalsIgnoreCase(requestMimeType)
            || DEFAULT_MULTIPART_TYPE.startsWith(requestMimeType);
        
        // type is a required parameter for multipart/related only
        if (xopOrMultipartRelated
            && requestMimeType.indexOf("type=") == -1) {
            if (xop) {
                ct.append("; type=\"application/xop+xml\"");
            } else {
                ct.append("; type=\"").append(bodyCt).append("\"");
            }    
        }
        
        // boundary
        ct.append("; boundary=\"")
            .append(bodyBoundary)
            .append("\"");
            
        String rootContentId = getHeaderValue("Content-ID", AttachmentUtil.BODY_ATTACHMENT_ID);
        
        // 'start' is a required parameter for XOP/MTOM, clearly defined
        // for simpler multipart/related payloads but is not needed for
        // multipart/mixed, multipart/form-data
        if (xopOrMultipartRelated) {
            ct.append("; start=\"<")
                .append(checkAngleBrackets(rootContentId))
                .append(">\"");
        }
        
        // start-info is a required parameter for XOP/MTOM, may be needed for
        // other WS cases but is redundant in simpler multipart/related cases
        // the parameters need to be included within the start-info's value in the escaped form
        if (writeOptionalTypeParameters || xop) {
            ct.append("; start-info=\"")
                .append(bodyCt);
            if (bodyCtParamsEscaped != null) {
                ct.append(bodyCtParamsEscaped);
            }
            ct.append("\"");
        }
        
        
        message.put(Message.CONTENT_TYPE, ct.toString());

        
        // 2. write headers
        out = message.getContent(OutputStream.class);
        encoding = (String) message.get(Message.ENCODING);
        if (encoding == null) {
            encoding = "UTF-8";
        }
        StringWriter writer = new StringWriter();
        writer.write("--");
        writer.write(bodyBoundary);
        
        StringBuilder mimeBodyCt = new StringBuilder();
        String bodyType = getHeaderValue("Content-Type", null);
        if (bodyType == null) {
            mimeBodyCt.append(xop ? "application/xop+xml" : bodyCt)
                .append("; charset=").append(encoding);
            if (xop) {
                mimeBodyCt.append("; type=\"").append(bodyCt);
                if (bodyCtParamsEscaped != null) {
                    mimeBodyCt.append(bodyCtParamsEscaped);
                }
                mimeBodyCt.append("\"");
            } else if (bodyCtParams != null) {
                mimeBodyCt.append(bodyCtParams);
            }
        } else {
            mimeBodyCt.append(bodyType);
        }
        
        writeHeaders(mimeBodyCt.toString(), rootContentId, rootHeaders, writer);
        out.write(writer.getBuffer().toString().getBytes(encoding));
    }

    private static String escapeQuotes(String s) {
        return s.indexOf('"') != 0 ? s.replace("\"", "\\\"") : s;    
    }

    private String getHeaderValue(String name, String defaultValue) {
        List<String> value = rootHeaders.get(name);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.size(); i++) {
            sb.append(value.get(i));
            if (i + 1 < value.size()) {
                sb.append(',');
            }
        }
        return sb.toString();
    }

    /**
     * 2-2-2016 ivashkulat, tschumacher
     * Big Hack for IRS web service requirement for 7bit content type encoding and content type
     ---------------------------------------------------------

     5.4.2 (from IRS documentation)
     Message Attachment Content Type ISS-A2AAIR web services require transmitters to use SOAP-over-HTTP messaging with
     MTOM to send XML data files.
     The file that is encoded in the MTOM attachment must be uncompressed native XML. The content type for the MTOM
     encoded binary object identified in
     the Manifest header must be �application/xml�. The content-transfer-encoding of the Form Data File must be 7-bit.

     Inside apache-cxf-3.1.4-src/core/src/main/java/org/apache/cxf/attachment/AttachmentSerializer.java
     194     private static void writeHeaders(String contentType, String attachmentId,
     195                                      Map<String, List<String>> headers, Writer writer) throws IOException {
     196 //        writer.write("\r\nContent-Type: ");
     197 //        writer.write(contentType);
     198 writer.write("\r\nContent-Type: application/xml");
     199 //        writer.write("\r\nContent-Transfer-Encoding: binary\r\n");
     200 writer.write("\r\nContent-Transfer-Encoding: 7bit\r\n");
     *
     *
     * ivashkulat 2-2-2016
     I am not overly comfortable with above hack, because  MTOM spec calls for base 64 bit attachments encoding
     and NOT 7-bit.
     IRS is essentially breaking MTOM spec...
     Here is my comment (mostly retorical):
     ------------------------------------------------------------------------------


     Am I misreading IRS doc here
     https://www.irs.gov/PUP/for_taxpros/software_developers/information_returns/AIR%20Submission%20Composition%20and
     %20Reference%20Guide%20TY2015.pdf

     , p.30 paragraph 5.2.1:

     The content type for the SOAP Envelop with MTOM encoded attachment must be “application/xop+xml” and the
     >>> content-transfer-encoding must be 8-bit.    <<<

     or IRS is in denial?

     Also, p.40, 5.4.1 Message Attachment File Format

     The Form Data File and the Error Data File will be in XML format and will be attached to the message using the
     W3C MTOM specification
     The Form Data File must be encoded in UTF-8 without BOM file format prior to MTOM encoding during submission.



     ivashkulat: here is example of properly encoded MTOM attachment, notice  "Content-Transfer-Encoding: binary"

     Content-Id: <rootpart*9e108924-ff3d-4b3b-b3df-a024e5e93956@example.jaxws.sun.com>
     Content-Type: application/xop+xml;charset=utf-8;type="text/xml"
     Content-Transfer-Encoding: binary

     MTOM is a BINARY encoded attachment, and not 7-bit encoded attachment.

     * @param contentType
     * @param attachmentId
     * @param headers
     * @param writer
     * @throws IOException
     */
    private static void writeHeaders(String contentType, String attachmentId, 
                                     Map<String, List<String>> headers, Writer writer) throws IOException {

        /* Original CXF 3.1.4 code:
        writer.write("\r\nContent-Type: ");
        writer.write(contentType);
        writer.write("\r\nContent-Transfer-Encoding: binary\r\n");
        */

        //Kaisers override to satisfy IRS web service (incorrect) requirement for encoding MTOM/XOP attachments:
        writer.write("\r\nContent-Type: application/xml");
        writer.write("\r\nContent-Transfer-Encoding: 7bit\r\n");
        //end of override


        if (attachmentId != null) {
            attachmentId = checkAngleBrackets(attachmentId);
            writer.write("Content-ID: <");
            writer.write(URLDecoder.decode(attachmentId, "UTF-8"));
            writer.write(">\r\n");
        }
        // headers like Content-Disposition need to be serialized
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            String name = entry.getKey();
            if ("Content-Type".equalsIgnoreCase(name) || "Content-ID".equalsIgnoreCase(name)
                || "Content-Transfer-Encoding".equalsIgnoreCase(name)) {
                continue;
            }
            writer.write(name);
            writer.write(": ");
            List<String> values = entry.getValue();
            for (int i = 0; i < values.size(); i++) {
                writer.write(values.get(i));
                if (i + 1 < values.size()) {
                    writer.write(",");
                }
            }
            writer.write("\r\n");
        }
        
        writer.write("\r\n");
    }

    private static String checkAngleBrackets(String value) { 
        if (value.charAt(0) == '<' && value.charAt(value.length() - 1) == '>') {
            return value.substring(1, value.length() - 1);
        }    
        return value;
    }
    
    /**
     * Write the end of the body boundary and any attachments included.
     * @throws IOException
     */
    public void writeAttachments() throws IOException {
        if (message.getAttachments() != null) {
            for (Attachment a : message.getAttachments()) {
                StringWriter writer = new StringWriter();                
                writer.write("\r\n--");
                writer.write(bodyBoundary);
                
                Map<String, List<String>> headers = null;
                Iterator<String> it = a.getHeaderNames();
                if (it.hasNext()) {
                    headers = new LinkedHashMap<String, List<String>>();
                    while (it.hasNext()) {
                        String key = it.next();
                        headers.put(key, Collections.singletonList(a.getHeader(key)));
                    }
                } else {
                    headers = Collections.emptyMap();
                }
                
                
                DataHandler handler = a.getDataHandler();
                handler.setCommandMap(AttachmentUtil.getCommandMap());
                
                writeHeaders(handler.getContentType(), a.getId(),
                             headers, writer);
                out.write(writer.getBuffer().toString().getBytes(encoding));
                handler.writeTo(out);
            }
        }
        StringWriter writer = new StringWriter();                
        writer.write("\r\n--");
        writer.write(bodyBoundary);
        writer.write("--");
        out.write(writer.getBuffer().toString().getBytes(encoding));
        out.flush();
    }

    public boolean isXop() {
        return xop;
    }

    public void setXop(boolean xop) {
        this.xop = xop;
    }

}
