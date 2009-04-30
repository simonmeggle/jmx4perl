/*
 * jmx4perl - WAR Agent for exporting JMX via JSON
 *
 * Copyright (C) 2009 Roland Huß, roland@cpan.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * Contact roland@cpan.org for any licensing questions.
 */

package org.cpan.jmx4perl;

import org.json.simple.JSONObject;

import javax.management.ObjectName;
import javax.management.MalformedObjectNameException;
import java.util.*;
import java.net.URLDecoder;
import java.io.UnsupportedEncodingException;

/**
 * A JMX request which knows how to translate from a REST Url. Additionally
 * it can be easily translated into a JSON format for inclusion into a response
 * from {@link org.cpan.jmx4perl.AgentServlet}
 * <p>
 * The REST-Url which gets recognized has the following format:
 * <p>
 * <pre>
 *    &lt;base_url&gt;/&lt;type&gt;/&lt;param1&gt;/&lt;param2&gt;/....
 * </pre>
 * <p>
 * where <code>base_url<code> is the URL specifying the overall servlet (including
 * the servlet context, something like "http://localhost:8080/j4p-agent"),
 * <code>type</code> the operational mode and <code>param1 .. paramN<code>
 * the provided parameters which are dependend on the <code>type<code>
 * <p>
 * The following types are recognized so far, along with there parameters:
 *
 * <ul>
 *   <li>Type: <b>read</b> ({@link Type#READ_ATTRIBUTE}<br/>
 *       Parameters: <code>param1<code> = MBean name, <code>param2</code> = Attribute name,
 *       <code>param3 ... paramN</code> = Inner Path.
 *       The inner path is optional and specifies a path into complex MBean attributes
 *       like collections or maps. If within collections/arrays/tabular data,
 *       <code>paramX</code> should specify
 *       a numeric index, in maps/composite data <code>paramX</code> is a used as a string
 *       key.</li>
 *   <li>Type: <b>write</b> ({@link Type#WRITE_ATTRIBUTE}<br/>
 *       Parameters: <code>param1</code> = MBean name, <code>param2</code> = Attribute name,
 *       <code>param3</code> = value, <code>param4 ... paramN</code> = Inner Path.
 *       The value must be URL encoded (with UTF-8 as charset), and must be convertable into
 *       a data structure</li>
 *   <li>Type: <b>exec</b> ({@link Type#EXEC_OPERATION}<br/>
 *       Parameters: <code>param1</code> = MBean name, <code>param2</code> = operation name,
 *       <code>param4 ... paramN</code> = arguments for the operation.
 *       The arguments must be URL encoded (with UTF-8 as charset), and must be convertable into
 *       a data structure</li>
 * </ul>
 * @author roland
 * @since Apr 19, 2009
 */
public class JmxRequest extends JSONObject {

    /**
     * Enumeration for encapsulationg the request mode.
     */
    enum Type {
        // Supported:
        READ_ATTRIBUTE("read"),
        LIST_MBEANS("list"),

        // Unsupported:
        WRITE_ATTRIBUTE("write"),
        EXEC_OPERATION("exec"),
        REGISTER_NOTIFICATION("regnotif"),
        REMOVE_NOTIFICATION("remnotif");

        private String value;

        Type(String pValue) {
            value = pValue;
        }

        public String getValue() {
            return value;
        }
    };


    private String objectNameS;
    private ObjectName objectName;
    private String attributeName;
    private String value;
    private List<String> extraArgs;
    private String operation;

    private Type type;

    JmxRequest(String pPathInfo) {
        try {
            if (pPathInfo != null && pPathInfo.length() > 0) {
                StringTokenizer tok = new StringTokenizer(pPathInfo,"/");
                String typeS = tok.nextToken();
                type = extractType(typeS);

                if (type != Type.LIST_MBEANS) {
                    objectNameS = tok.nextToken();
                    objectName = new ObjectName(objectNameS);
                    if (type == Type.READ_ATTRIBUTE || type == Type.WRITE_ATTRIBUTE) {
                        attributeName = tok.nextToken();
                        if (type == Type.WRITE_ATTRIBUTE) {
                            value = URLDecoder.decode(tok.nextToken(),"UTF-8");
                        }
                    } else if (type == Type.EXEC_OPERATION) {
                        operation = tok.nextToken();
                    } else {
                        throw new UnsupportedOperationException("Type " + type + " is not supported (yet)");
                    }
                }

                // Extract all additional args from the remaining path info
                extraArgs = new ArrayList<String>();
                while (tok.hasMoreTokens()) {
                    extraArgs.add(tok.nextToken());
                }
                setupJSON();
            }
        } catch (NoSuchElementException exp) {
            throw new IllegalArgumentException("Invalid path info " + pPathInfo,exp);
        } catch (MalformedObjectNameException e) {
            throw new IllegalArgumentException(
                    "Invalid object name " + objectNameS +
                            ": " + e.getMessage(),e);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("Internal: Illegal encoding for URL conversion: " + e,e);
        }
    }

    private Type extractType(String pTypeS) {
        for (Type t : Type.values()) {
            if (t.getValue().equals(pTypeS)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Invalid request type '" + pTypeS + "'");
    }

    private void setupJSON() {
        put("type",type.getValue());
        if (type == Type.READ_ATTRIBUTE || type == Type.WRITE_ATTRIBUTE) {
            put("attribute",getAttributeName());
        }
        if (extraArgs.size() > 0) {
            StringBuffer buf = new StringBuffer();
            Iterator<String> it = extraArgs.iterator();
            while (it.hasNext()) {
                buf.append(it.next());
                if (it.hasNext()) {
                    buf.append("/");
                }
            }
            put("path",buf.toString());
        }
        if (type != Type.LIST_MBEANS) {
            put("mbean",objectName.getCanonicalName());
        }
    }

    public String getObjectNameAsString() {
        return objectNameS;
    }

    public ObjectName getObjectName() {
        return objectName;
    }

    public String getAttributeName() {
        return attributeName;
    }

    public List<String> getExtraArgs() {
        return extraArgs;
    }

    public String getValue() {
        return value;
    }

    public Type getType() {
        return type;
    }

    public String getOperation() {
        return operation;
    }
}
