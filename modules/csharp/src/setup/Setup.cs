﻿/*
 * Copyright (c) 1998-2010 Caucho Technology -- all rights reserved
 *
 * This file is part of Resin(R) Open Source
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Resin Open Source is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2
 * as published by the Free Software Foundation.
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
 * @author Alex Rojkov
 */
using System;
using System.Collections;
using System.Collections.Generic;
using System.Configuration.Install;
using System.DirectoryServices;
using System.IO;
using System.Runtime.Serialization.Formatters.Binary;
using System.ServiceProcess;
using System.Text;
using System.Windows.Forms;
using Microsoft.Win32;

namespace Caucho
{
  public class Setup
  {
    public static String REG_SERVICES = "SYSTEM\\CurrentControlSet\\Services";

    private String _resinHome;
    private String _apacheHome;
    private Resin _resin;
    public Resin Resin
    {
      get { return _resin; }
      set
      {
        if (_resin != null)
          throw new ApplicationException();
        _resin = value;
      }
    }
    private List<Resin> _resinList = new List<Resin>();
    private List<ResinService> _resinServices = new List<ResinService>();
    private List<String> _users = null;

    private Apache _apache;
    private HashSet<Apache> _apacheSet;

    private String _iisScripts;
    private ArrayList _apacheHomeSet;

    public String ResinHome
    {
      get { return _resinHome; }
      set { _resinHome = value; }
    }

    public String ApacheHome
    {
      get { return _apacheHome; }
      set { _apacheHome = value; }
    }

    public ArrayList ApacheHomeSet
    {
      get { return _apacheHomeSet; }
    }

    public String IISScripts
    {
      get { return _iisScripts; }
    }

    public Setup()
    {
      String path = System.Reflection.Assembly.GetExecutingAssembly().Location;
      this.ResinHome = Util.GetResinHome(null, path);

      this._apacheHomeSet = new DirSet();

      FindResinServices();
      FindResin();

      Apache.FindApache(_apacheHomeSet);

      _iisScripts = FindIIS();
    }

    public void FindResin()
    {
      DriveInfo[] drives = DriveInfo.GetDrives();
      foreach (DriveInfo drive in drives) {
        if (DriveType.Fixed != drive.DriveType && DriveType.Ram != drive.DriveType)
          continue;
        DirectoryInfo root = drive.RootDirectory;
        DirectoryInfo[] directories = root.GetDirectories();
        foreach (DirectoryInfo directory in directories) {
          if (directory.Name.StartsWith("resin", StringComparison.CurrentCultureIgnoreCase)
            && Util.IsResinHome(directory.FullName)) {
            Resin resin = new Resin(Util.Canonicalize(directory.FullName));
            if (!HasResin(resin))
              AddResin(resin);
          } else if (directory.Name.Contains("appservers")) {
            DirectoryInfo[] appserverDirectories = directory.GetDirectories();
            foreach (DirectoryInfo appserverDir in appserverDirectories) {
              if (Util.IsResinHome(appserverDir.FullName)) {
                String home = Util.Canonicalize(appserverDir.FullName);
                Resin resin = new Resin(home);
                if (!HasResin(resin))
                  AddResin(resin);
              }
            }
          }
        }
      }

      String currentResin = Util.GetCurrentResinFromRegistry();
      if (currentResin != null) {
        currentResin = Util.Canonicalize(currentResin);
        Resin resin = new Resin(currentResin);

        Resin = resin;

        if (!HasResin(resin))
          AddResin(resin);
      }

      RegistryKey services = Registry.LocalMachine.OpenSubKey(Setup.REG_SERVICES);
      foreach (String name in services.GetSubKeyNames()) {
        RegistryKey key = services.OpenSubKey(name);
        Object imagePathObj = key.GetValue("ImagePath");
        if (imagePathObj == null && !"".Equals(imagePathObj))
          continue;

        String imagePath = (String)imagePathObj;
        String lowerCaseImagePath = imagePath.ToLower();

        if (imagePath.IndexOf("resin.exe") != -1) {
          ResinArgs resinArgs = new ResinArgs(imagePath);
          Resin resin = null;
          if (resinArgs.Home != null) {
            resin = new Resin(resinArgs.Home);
          } else if (resinArgs.Exe != null) {
            String exe = resinArgs.Exe;
            String home = exe.Substring(0, exe.Length - 10);
            if (Util.IsResinHome(home))
              resin = new Resin(home);
          }

          if (resin != null && !HasResin(resin))
            AddResin(resin);
        }

        key.Close();
      }

      services.Close();

      String path = Util.Canonicalize(System.Reflection.Assembly.GetExecutingAssembly().Location);
      while (path.LastIndexOf('\\') > 0) {
        path = path.Substring(0, path.LastIndexOf('\\'));
        if (Util.IsResinHome(path)) {
          Resin resin = new Resin(path);
          if (Resin == null)
            Resin = resin;

          if (!HasResin(resin)) {
            AddResin(resin);
          }

          break;
        };
      }
    }

    public void FindResinServices()
    {
      RegistryKey services = Registry.LocalMachine.OpenSubKey(Setup.REG_SERVICES);
      foreach (String name in services.GetSubKeyNames()) {
        RegistryKey key = services.OpenSubKey(name);
        Object imagePathObj = key.GetValue("ImagePath");
        if (imagePathObj == null && !"".Equals(imagePathObj))
          continue;

        String imagePath = (String)imagePathObj;
        String lowerCaseImagePath = imagePath.ToLower();

        if ((imagePath.IndexOf("resin.exe") > 0 || imagePath.IndexOf("httpd.exe") > 0) && imagePath.IndexOf("-service") > 0) {
          ResinArgs resinArgs = new ResinArgs(imagePath);

          ResinService resin = null;
          if (resinArgs.Home != null) {
            resin = new ResinService();
            resin.Home = resinArgs.Home;
          } else if (resinArgs.Exe != null) {
            String exe = resinArgs.Exe;
            String home = exe.Substring(0, exe.Length - 10);
            if (Util.IsResinHome(home)) {
              resin = new ResinService();
              resin.Home = home;
            }
          } 

          resin.Exe = resinArgs.Exe;

          if (resin == null)
            continue;

          resin.Name = name;
          resin.Server = resinArgs.Server;
          resin.Root = resinArgs.Root;
          resin.Log = resinArgs.Log;
          resin.User = resinArgs.User;
          resin.JavaHome = resinArgs.JavaHome;
          if (resinArgs.JmxPort != null && !"".Equals(resinArgs.JmxPort))
            resin.JmxPort = int.Parse(resinArgs.JmxPort);

          if (resinArgs.DebugPort != null && !"".Equals(resinArgs.DebugPort))
            resin.DebugPort = int.Parse(resinArgs.DebugPort);

          if (resinArgs.WatchDogPort != null && !"".Equals(resinArgs.WatchDogPort))
            resin.WatchdogPort = int.Parse(resinArgs.WatchDogPort);

          resin.IsPreview = resinArgs.IsPreview;

          resin.ExtraParams = resinArgs.ResinArguments;

          AddResinService(resin);
        }

        key.Close();
      }

      services.Close();
    }

    public ResinConf GetResinConf(String conf)
    {
      return new ResinConf(conf);
    }

    public bool HasResin(Resin resin)
    {
      return _resinList.Contains(resin);
    }

    public void AddResin(Resin resin)
    {
      _resinList.Add(resin);
    }

    public IList GetResinList()
    {
      IList result = new List<Resin>(_resinList);
      return result;
    }

    public Resin SelectResin(String home)
    {
      home = Util.Canonicalize(home);

      Resin result = null;

      foreach (Resin resin in _resinList) {
        if (home.Equals(resin.Home))
          result = resin;
      }

      if (result == null) {
        result = new Resin(home);
        AddResin(result);
      }

      return result;
    }

    public bool HasResinService(ResinService service)
    {
      return _resinServices.Contains(service);
    }

    public void AddResinService(ResinService service)
    {
      _resinServices.Add(service);
    }

    public IList<ResinService> GetResinServices(Resin resin)
    {
      IList<ResinService> result = new List<ResinService>();
      foreach (ResinService resinService in _resinServices) {
        if (resin.Home.Equals(resinService.Home))
          result.Add(resinService);
      }

      return result;
    }

    public void ResetResinServices()
    {
      _resinServices.Clear();
      FindResinServices();
    }

    public IList<ResinService> GetResinServices()
    {
      return _resinServices;
    }

    public String GetResinConfFile(Resin resin)
    {
      if (File.Exists(resin.Home + @"\conf\resin.xml"))
        return @"conf\resin.xml";
      else if (File.Exists(resin.Home + @"\conf\resin.conf"))
        return @"conf\resin.conf";
      else
        return null;
    }

    public List<String> GetUsers()
    {
      if (_users == null) {
        List<String> users = new List<String>();
        users.Add("Local Service");
        DirectoryEntry groupEntry = new DirectoryEntry("WinNT://.");
        groupEntry.Children.SchemaFilter.Add("User");
        IEnumerator e = groupEntry.Children.GetEnumerator();
        while (e.MoveNext()) {
          String name = ((DirectoryEntry)e.Current).Name;
          if (!"Guest".Equals(name))
            users.Add(name);
        }

        _users = users;
      }

      return _users;
    }

    public ConfigureInfo SetupIIS(String resinHome, String iisScripts)
    {
      ConfigureInfo configInfo = new ConfigureInfo();

      DirectoryEntry filters = new DirectoryEntry("IIS://localhost/W3SVC/Filters");
      DirectoryEntry resinFilter = null;

      foreach (DirectoryEntry entry in filters.Children) {
        if ("Resin".Equals(entry.Name)) {
          resinFilter = entry;
        }
      }

      if (resinFilter == null)
        resinFilter = filters.Children.Add("Resin", "IIsFilter");

      resinFilter.Properties["FilterEnabled"][0] = true;
      resinFilter.Properties["FilterState"][0] = 4;
      resinFilter.Properties["KeyType"][0] = "IIsFilter";
      resinFilter.Properties["FilterPath"][0] = iisScripts + "\\isapi_srun.dll";
      resinFilter.Properties["FilterDescription"][0] = "isapi_srun Extension";

      PropertyValueCollection filterOrder = (PropertyValueCollection)filters.Properties["FilterLoadOrder"];
      String val = (String)filterOrder[0];

      if (!val.Contains("Resin,"))
        filterOrder[0] = "Resin," + val;

      resinFilter.CommitChanges();
      resinFilter.Close();
      filters.CommitChanges();
      filters.Close();

      try {
        CopyIsapiFilter(resinHome, iisScripts);
        configInfo.Status = ConfigureInfo.SETUP_OK;
      }
      catch (Exception e) {
        configInfo.Status = ConfigureInfo.ISAPI_IO_ERROR;
        configInfo.Exception = e;
      }

      return configInfo;
    }

    public void CopyIsapiFilter(String resinHome, String iisScripts)
    {
      String filterPath = iisScripts + "\\isapi_srun.dll";
      if (File.Exists(filterPath))
        File.Delete(filterPath);

      File.Copy(resinHome + "\\win32\\isapi_srun.dll", filterPath);
    }

    public void RemoveIsapiFilter(String iisScripts)
    {
      String filterPath = iisScripts + "\\isapi_srun.dll";
      File.Delete(filterPath);
    }

    public void StopIIS()
    {
      ServiceController sc = new ServiceController("W3SVC");

      if (sc.Status == ServiceControllerStatus.Running) {
        sc.Stop();
        sc.WaitForStatus(ServiceControllerStatus.Stopped);
      }

      sc.Close();
    }

    public ConfigureInfo RemoveIIS(String iisScripts)
    {
      ConfigureInfo configInfo = new ConfigureInfo();

      DirectoryEntry filters = new DirectoryEntry("IIS://localhost/W3SVC/Filters");
      DirectoryEntry resinFilter = null;

      foreach (DirectoryEntry entry in filters.Children) {
        if ("Resin".Equals(entry.Name)) {
          resinFilter = entry;
        }
      }

      bool resinFound = false;
      if (resinFilter != null) {
        filters.Children.Remove(resinFilter);
        resinFound = true;
      }

      PropertyValueCollection filterOrder = (PropertyValueCollection)filters.Properties["FilterLoadOrder"];
      String val = (String)filterOrder[0];

      int index = val.IndexOf("Resin,");

      if (index != -1) {
        String newVal = val.Substring(0, index) + val.Substring(index + 6, val.Length - 6 - index);
        filterOrder[0] = newVal;
        resinFound = true;
      }

      filters.CommitChanges();
      filters.Close();

      try {
        String filterPath = iisScripts + "\\isapi_srun.dll";
        if (File.Exists(filterPath))
          File.Delete(filterPath);

        if (resinFound)
          configInfo.Status = ConfigureInfo.REMOVED_OK;
        else
          configInfo.Status = ConfigureInfo.REMOVED_ALREADY;
      }
      catch (Exception e) {
        configInfo.Status = ConfigureInfo.ISAPI_IO_ERROR;
        configInfo.Exception = e;
      }

      return configInfo;
    }

    private String FindIIS()
    {
      String result = null;

      DirectoryEntry entry = null;
      try {
        entry = new DirectoryEntry("IIS://localhost/W3SVC/1/ROOT/scripts");

        if (entry.Properties != null) {
          Object val = entry.Properties["Path"];
          if (val != null && (val is PropertyValueCollection)) {
            PropertyValueCollection collection = (PropertyValueCollection)val;
            IEnumerator enumerator = collection.GetEnumerator();

            if (enumerator.MoveNext())
              result = (String)enumerator.Current;
          }
        }
      }
      catch (Exception e) {
        Console.Out.WriteLine(e.ToString());
      }
      finally {
        if (entry != null)
          entry.Close();
      }

      return result;
    }

    public void RestartIIS()
    {
      RestartService("W3SVC");
    }

    public void RestartService(String serviceName)
    {
      ServiceController sc = new ServiceController(serviceName);

      if (sc.Status == ServiceControllerStatus.Running) {
        sc.Stop();
        sc.WaitForStatus(ServiceControllerStatus.Stopped);
      }

      sc.Start();
      sc.WaitForStatus(ServiceControllerStatus.Running);

      sc.Close();
    }

    public void InstallService(ResinService resinService, bool isNew)
    {
      if (isNew) {
        Installer installer = InitInstaller(resinService);
        Hashtable installState = new Hashtable();
        installer.Install(installState);
        StoreState(installState, resinService.Name);
      }

      RegistryKey system = Registry.LocalMachine.OpenSubKey("System");
      RegistryKey currentControlSet = system.OpenSubKey("CurrentControlSet");
      RegistryKey servicesKey = currentControlSet.OpenSubKey("Services");
      RegistryKey serviceKey = servicesKey.OpenSubKey(resinService.Name, true);
      String imagePath = (String)serviceKey.GetValue("ImagePath");
      if (imagePath.Contains(".exe\""))
        imagePath = imagePath.Substring(0, imagePath.IndexOf(".exe\"") + 5);
      else if (imagePath.Contains(".exe"))
        imagePath = imagePath.Substring(0, imagePath.IndexOf(".exe\"") + 4);

      StringBuilder builder = new StringBuilder(imagePath);
      builder.Append(' ').Append(resinService.GetServiceArgs());

      serviceKey.SetValue("ImagePath", builder.ToString());
    }

    public void UninstallService(ResinService resinService)
    {
      Hashtable state = LoadState(resinService.Name);

      Installer installer = InitInstaller(resinService);

      installer.Uninstall(state);
    }

    private Installer InitInstaller(ResinService resinService)
    {
      TransactedInstaller txInst = new TransactedInstaller();
      txInst.Context = new InstallContext(null, new String[] { });
      txInst.Context.Parameters["assemblypath"] = resinService.Exe;

      ServiceProcessInstaller spInst = new ServiceProcessInstaller();
      if (resinService.User != null) {
        spInst.Username = resinService.User;
        spInst.Password = resinService.Password;
        spInst.Account = ServiceAccount.User;
      } else {
        spInst.Account = ServiceAccount.LocalSystem;
      }

      txInst.Installers.Add(spInst);

      ServiceInstaller srvInst = new ServiceInstaller();
      srvInst.ServiceName = resinService.Name;
      srvInst.DisplayName = resinService.Name;
      srvInst.StartType = ServiceStartMode.Manual;

      txInst.Installers.Add(srvInst);

      return txInst;
    }

    private void StoreState(Hashtable state, String serviceName)
    {
      String dir = Environment.SpecialFolder.CommonApplicationData.ToString() + @"\Caucho\services";
      DirectoryInfo info;
      if ((info = Directory.CreateDirectory(dir)) != null && info.Exists) {
        String file = dir + @"\" + serviceName + ".srv";

        FileStream fs = new FileStream(file, FileMode.Create, FileAccess.Write);
        BinaryFormatter serializer = new BinaryFormatter();
        serializer.Serialize(fs, state);
        fs.Flush();
        fs.Close();
      }
    }

    private Hashtable LoadState(String serviceName)
    {
      String file = Environment.SpecialFolder.CommonApplicationData.ToString() + @"\Caucho\services\" + serviceName + ".srv";
      if (File.Exists(file) && false) {
        Hashtable state = null;
        FileStream fs = new FileStream(file, FileMode.Open, FileAccess.Read);
        BinaryFormatter serializer = new BinaryFormatter();
        state = (Hashtable)serializer.Deserialize(fs);
        fs.Close();
        return state;
      } else {
        return FakeState();
      }
    }

    [STAThread]
    public static void Main(String[] args)
    {
      Application.EnableVisualStyles();
      Application.SetCompatibleTextRenderingDefault(false);
      SetupForm setupForm = new SetupForm(new Setup());
      Application.Run(setupForm);
    }

    public static Hashtable FakeState()
    {
      Hashtable state = new Hashtable();
      IDictionary[] states = new IDictionary[2];
      states[0] = new Hashtable();
      states[0]["_reserved_nestedSavedStates"] = new IDictionary[0];
      states[0]["Account"] = ServiceAccount.LocalSystem;

      states[1] = new Hashtable();
      IDictionary[] substates = new IDictionary[1];
      substates[0] = new Hashtable();
      substates[0]["_reserved_nestedSavedStates"] = new IDictionary[0];
      substates[0]["alreadyRegistered"] = false;
      substates[0]["logExists"] = true;
      substates[0]["baseInstalledAndPlatformOK"] = true;

      states[1]["_reserved_nestedSavedStates"] = substates;
      states[1]["installed"] = true;

      state["_reserved_nestedSavedStates"] = states;

      return state;
    }
  }

  class DirSet : ArrayList
  {
    public override int Add(object value)
    {
      int index = base.IndexOf(value);

      if (index != -1)
        return index;

      return base.Add(value);
    }
  }

  public class ConfigureInfo
  {
    public static int SETUP_ALREADY = 1;
    public static int SETUP_OK = 2;
    public static int ISAPI_IO_ERROR = 3;
    public static int REMOVED_OK = 4;
    public static int REMOVED_ALREADY = 5;

    private String _backupFile;
    private String _serviceName;
    private int _status;
    private Exception _exception;

    public String BackUpFile
    {
      set { _backupFile = value; }
      get { return _backupFile; }
    }

    public String ServiceName
    {
      set { _serviceName = value; }
      get { return _serviceName; }
    }

    public int Status
    {
      set { _status = value; }
      get { return _status; }
    }

    public Exception Exception
    {
      set { _exception = value; }
      get { return _exception; }
    }
  }

  public class Resin : IEquatable<Resin>
  {
    public String Home { get; set; }
    public String[] Servers { get; set; }

    public Resin(String home)
    {
      Home = home;
    }

    public override int GetHashCode()
    {
      return Home.GetHashCode();
    }

    #region IEquatable Members
    public bool Equals(Resin obj)
    {
      return Home.Equals(obj.Home);
    }
    #endregion

    public override string ToString()
    {
      return Home;
    }
  }

  public class ResinService : IEquatable<ResinService>, ICloneable
  {
    public String Exe { get; set; }
    public String Home { get; set; }
    public String Root { get; set; }
    public String Log { get; set; }
    public String Conf { get; set; }
    public String Name { get; set; }
    public String User { get; set; }
    public String Password { get; set; }
    public bool IsPreview { get; set; }
    public String JavaHome { get; set; }
    public String Server { get; set; }
    public String DynamicServer { get; set; }
    public int DebugPort { get; set; }
    public int JmxPort { get; set; }
    public int WatchdogPort { get; set; }
    public String ExtraParams { get; set; }

    public ResinService()
    {
      JmxPort = -1;
      DebugPort = -1;
      WatchdogPort = -1;
      IsPreview = false;
    }

    public String GetServiceArgs()
    {
      StringBuilder sb = new StringBuilder();
      sb.Append("-service -name ");
      sb.Append(Name);
      if (Conf != null)
        sb.Append(" -conf ").Append(Conf);

      sb.Append(" -resin-home ").Append(Home);

      if (Root != null)
        sb.Append(" -root-directory ").Append(Root);

      if (Log != null)
        sb.Append(" -log-directory ").Append(Log);

      if (Server != null && !"".Equals(Server))
        sb.Append(" -server ").Append(Server);
      else if (DynamicServer != null)
        sb.Append(" -dynamic-server ").Append(DynamicServer);

      if (IsPreview)
        sb.Append(" -preview");

      if (DebugPort > 0)
        sb.Append(" -debug-port ").Append(DebugPort.ToString());

      if (JmxPort > 0)
        sb.Append(" -jmx-port ").Append(JmxPort.ToString());

      if (WatchdogPort > 0)
        sb.Append(" -watchdog-port ").Append(WatchdogPort.ToString());

      if (ExtraParams != null)
        sb.Append(" ").Append(ExtraParams);

      return sb.ToString();
    }

    public override int GetHashCode()
    {
      return Name.GetHashCode();
    }

    public bool Equals(ResinService resinService)
    {
      if (this == resinService)
        return true;

      return Name.Equals(resinService);
    }

    public override String ToString()
    {
      StringBuilder result = new StringBuilder(Name);
      result.Append(" [");


      if (Server != null)
        result.Append("-server ").Append(Server);
      else
        result.Append("default server");

      result.Append(']');

      return result.ToString();
    }

    #region ICloneable Members

    public object Clone()
    {
      return this.MemberwiseClone();
    }

    #endregion
  }

  class StateNofFoundException : Exception
  {
  }
}
