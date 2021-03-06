/*
 * Copyright (C) 2013 - 2018, Logical Clocks AB and RISE SICS AB. All rights reserved
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS  OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL  THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR  OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package io.hops.hopsworks.common.dao.user.ldap;

import java.util.List;
import javax.xml.bind.annotation.XmlRootElement;
import org.codehaus.jackson.annotate.JsonIgnore;

@XmlRootElement
public class LdapUserDTO {
  private String entryUUID;
  private String uid;
  private String givenName;
  private String sn;
  private List<String> email;

  public LdapUserDTO() {
  }

  public LdapUserDTO(String entryUUID, String uid, String givenName, String sn, List<String> email) {
    this.entryUUID = entryUUID;
    this.uid = uid;
    this.givenName = givenName;
    this.sn = sn;
    this.email = email;
  }

  @JsonIgnore
  public String getEntryUUID() {
    return entryUUID;
  }

  public void setEntryUUID(String entryUUID) {
    this.entryUUID = entryUUID;
  }

  public String getUid() {
    return uid;
  }

  public void setUid(String uid) {
    this.uid = uid;
  }

  public String getGivenName() {
    return givenName;
  }

  public void setGivenName(String givenName) {
    this.givenName = givenName;
  }

  public String getSn() {
    return sn;
  }

  public void setSn(String sn) {
    this.sn = sn;
  }

  public List<String> getEmail() {
    return email;
  }

  public void setEmail(List<String> email) {
    this.email = email;
  }

  @Override
  public String toString() {
    return "LdapUserDTO{" + "entryUUID=" + entryUUID + ", uid=" + uid + ", givenName=" + givenName + ", sn=" + sn +
        ", email=" + email + '}';
  }
  
}
