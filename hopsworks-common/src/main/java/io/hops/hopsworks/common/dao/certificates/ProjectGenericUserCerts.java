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

package io.hops.hopsworks.common.dao.certificates;

import java.io.Serializable;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlRootElement;

@Entity
@Table(name = "projectgenericuser_certs",
        catalog = "hopsworks",
        schema = "")
@XmlRootElement
@NamedQueries({
  @NamedQuery(name = "ProjectGenericUserCerts.findAll",
          query = "SELECT s FROM ProjectGenericUserCerts s"),
  @NamedQuery(name = "ProjectGenericUserCerts.findByProjectGenericUsername",
          query
          = "SELECT s FROM ProjectGenericUserCerts s WHERE s.projectGenericUsername = :projectGenericUsername")})

public class ProjectGenericUserCerts implements Serializable {

  private static final long serialVersionUID = 1L;
  @Id
  @Basic(optional = false)
  @NotNull
  @Size(min = 1,
          max = 20)
  @Column(name = "project_generic_username")
  private String projectGenericUsername;
  @Lob
  @Column(name = "pgu_key")
  private byte[] key;
  @Lob
  @Column(name = "pgu_cert")
  private byte[] cert;
  @NotNull
  @Column(name = "cert_password")
  private String certificatePassword;

  public ProjectGenericUserCerts() {
  }

  public ProjectGenericUserCerts(String projectGenericUsername) {
    this.projectGenericUsername = projectGenericUsername;
  }

  public String getProjectGenericUsername() { return projectGenericUsername; }

  public void setProjectGenericUsername(String projectGenericUsername) {
    this.projectGenericUsername= projectGenericUsername;
  }

  public byte[] getKey() { return key; }

  public void setKey(byte[] key) { this.key = key; }

  public byte[] getCert() { return cert; }

  public void setCert(byte[] cert) { this.cert = cert; }

  public String getCertificatePassword() {
    return certificatePassword;
  }
  
  public void setCertificatePassword(String certificatePassword) {
    this.certificatePassword = certificatePassword;
  }
  
  @Override
  public int hashCode() {
    int hash = 0;
    hash += (projectGenericUsername != null ? projectGenericUsername.hashCode() : 0);
    return hash;
  }

  @Override
  public boolean equals(Object object) {
    // TODO: Warning - this method won't work in the case the id fields are not set
    if (!(object instanceof ProjectGenericUserCerts)) {
      return false;
    }
    ProjectGenericUserCerts other = (ProjectGenericUserCerts) object;
    if ((this.projectGenericUsername == null && other.projectGenericUsername != null)
            || (this.projectGenericUsername != null && !this.projectGenericUsername.equals(
                    other.projectGenericUsername))) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "se.kth.hopsworks.certificates.ProjectGenericUserCerts[ projectGenericUsername="
            + projectGenericUsername + " ]";
  }

}
