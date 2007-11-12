/*
 * Copyright (c) 1998-2007 Caucho Technology -- all rights reserved
 *
 * This file is part of Resin(R) Open Source
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Resin Open Source is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Resin Open Source is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, or any warranty
 * of NON-INFRINGEMENT.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Resin Open Source; if not, write to the
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Sam
 */


package com.caucho.netbeans.customizer;

public class CustomizerDeploy
  extends javax.swing.JPanel
{

  private final CustomizerDataSupport custData;

  /**
   * Creates new form CustomizerDeploy
   */
  public CustomizerDeploy(CustomizerDataSupport custData)
  {
    this.custData = custData;
    initComponents();
  }

  /**
   * This method is called from within the constructor to initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is always
   * regenerated by the Form Editor.
   */
  // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
  private void initComponents()
  {
    jCheckBox1 = new javax.swing.JCheckBox();

    jCheckBox1.setText(org.openide.util.NbBundle.getMessage(CustomizerDeploy.class,
                                                            "LBL_AutoDeploy"));
    jCheckBox1.setToolTipText(org.openide.util.NbBundle.getMessage(
      CustomizerDeploy.class,
      "MSG_AutoloadDesc"));
    jCheckBox1.setBorder(javax.swing.BorderFactory.createEmptyBorder(0,
                                                                     0,
                                                                     0,
                                                                     0));
    jCheckBox1.setMargin(new java.awt.Insets(0, 0, 0, 0));
    jCheckBox1.setModel(custData.getAutoloadModel());

    org.jdesktop.layout.GroupLayout layout
      = new org.jdesktop.layout.GroupLayout(this);
    this.setLayout(layout);
    layout.setHorizontalGroup(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
      .add(layout.createSequentialGroup()
      .addContainerGap()
      .add(jCheckBox1)
      .addContainerGap()));
    layout.setVerticalGroup(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
      .add(layout.createSequentialGroup()
      .addContainerGap()
      .add(jCheckBox1)
      .addContainerGap(117, Short.MAX_VALUE)));
  }// </editor-fold>//GEN-END:initComponents


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JCheckBox jCheckBox1;
  // End of variables declaration//GEN-END:variables

}
