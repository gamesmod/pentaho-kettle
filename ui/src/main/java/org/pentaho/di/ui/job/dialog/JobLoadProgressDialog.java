/*! ******************************************************************************
 *
 * Pentaho
 *
 * Copyright (C) 2024 by Hitachi Vantara, LLC : http://www.pentaho.com
 *
 * Use of this software is governed by the Business Source License included
 * in the LICENSE.TXT file.
 *
 * Change Date: 2029-07-20
 ******************************************************************************/


package org.pentaho.di.ui.job.dialog;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.widgets.Shell;
import org.pentaho.di.core.ProgressMonitorAdapter;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.extension.ExtensionPointHandler;
import org.pentaho.di.core.extension.KettleExtensionPoint;
import org.pentaho.di.job.JobMeta;
import org.pentaho.di.repository.KettleRepositoryLostException;
import org.pentaho.di.repository.ObjectId;
import org.pentaho.di.repository.Repository;
import org.pentaho.di.repository.RepositoryDirectoryInterface;
import org.pentaho.di.ui.core.dialog.ErrorDialog;
import org.pentaho.di.ui.job.entries.missing.MissingEntryDialog;
import org.pentaho.di.ui.spoon.Spoon;

/**
 *
 *
 * @author Matt
 * @since 13-mrt-2005
 */
public class JobLoadProgressDialog {
  private Shell shell;
  private Repository rep;
  private String jobname;
  private RepositoryDirectoryInterface repdir;
  private JobMeta jobInfo;
  private String versionLabel;
  private ObjectId objectId;

  /**
   * Creates a new dialog that will handle the wait while loading a job...
   */
  public JobLoadProgressDialog( Shell shell, Repository rep, String jobname, RepositoryDirectoryInterface repdir,
    String versionLabel ) {
    this.shell = shell;
    this.rep = rep;
    this.jobname = jobname;
    this.repdir = repdir;
    this.versionLabel = versionLabel;

    this.jobInfo = null;
  }

  /**
   * Creates a new dialog that will handle the wait while loading a job...
   */
  public JobLoadProgressDialog( Shell shell, Repository rep, ObjectId objectId, String versionLabel ) {
    this.shell = shell;
    this.rep = rep;
    this.objectId = objectId;
    this.versionLabel = versionLabel;

    this.jobInfo = null;
  }

  public JobMeta open() {
    IRunnableWithProgress op = new IRunnableWithProgress() {
      public void run( IProgressMonitor monitor ) throws InvocationTargetException, InterruptedException {
        Spoon spoon = Spoon.getInstance();
        try {
          // Call extension point(s) before the file has been opened
          ExtensionPointHandler.callExtensionPoint(
            spoon.getLog(),
            KettleExtensionPoint.JobBeforeOpen.id,
            ( objectId == null ) ? jobname : objectId.toString() );

          if ( objectId != null ) {
            jobInfo = rep.loadJob( objectId, versionLabel );
          } else {
            jobInfo = rep.loadJob( jobname, repdir, new ProgressMonitorAdapter( monitor ), versionLabel );
          }

          // Call extension point(s) now that the file has been opened
          ExtensionPointHandler.callExtensionPoint( spoon.getLog(), KettleExtensionPoint.JobAfterOpen.id, jobInfo );

          if ( jobInfo.hasMissingPlugins() ) {
            MissingEntryDialog missingDialog = new MissingEntryDialog( shell, jobInfo.getMissingEntries() );
            if ( missingDialog.open() == null ) {
              jobInfo = null;
            }
          }
        } catch ( KettleException e ) {
          throw new InvocationTargetException( e, "Error loading job" );
        }
      }
    };

    try {
      ProgressMonitorDialog pmd = new ProgressMonitorDialog( shell );
      pmd.run( true, false, op );
    } catch ( InvocationTargetException e ) {
      KettleRepositoryLostException krle = KettleRepositoryLostException.lookupStackStrace( e );
      if ( krle != null ) {
        throw krle;
      }
      new ErrorDialog( shell, "Error loading job", "An error occured loading the job!", e );
      jobInfo = null;
    } catch ( InterruptedException e ) {
      new ErrorDialog( shell, "Error loading job", "An error occured loading the job!", e );
      jobInfo = null;
    }

    return jobInfo;
  }
}
