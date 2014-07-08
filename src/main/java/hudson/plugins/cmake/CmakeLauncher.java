package hudson.plugins.cmake;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.util.ArgumentListBuilder;

import java.io.IOException;

public class CmakeLauncher {
    private final Launcher launcher;
    private final EnvVars envs;
    private final FilePath workSpace;
    private final BuildListener listener;
    private final String buildDir;

    public CmakeLauncher(Launcher launcher,
                         EnvVars envs,
                         FilePath workSpace,
                         BuildListener listener,
                         String buildDir) {
        super();
        this.launcher = launcher;
        this.envs = envs;
        this.workSpace = workSpace;
        this.listener = listener;
        this.buildDir = buildDir;
    }

//	public boolean launchCmake(String cmakeCall) throws IOException, InterruptedException {
//        int result = this.launcher.launch()
//                .cmds(cmakeCall)
//                .envs(this.envs)
//                .stdout(this.listener.getLogger())
//                .stderr(this.listener.getLogger())
//                .pwd(new FilePath(this.workSpace, this.buildDir))
//                .join();
//		return (result == 0);
//	}

    public boolean launchCmake(String cmakeBin,
                               String generator,
                               String preloadScript,
                               String theSourceDir,
                               String theInstallDir,
                               String theBuildType,
                               String cmakeArgs)
    {
        ArgumentListBuilder args = new ArgumentListBuilder();

        args.add(cmakeBin);

        if (preloadScript != null && !preloadScript.trim().isEmpty()) {
            args.add("-C", preloadScript);
        }

        args.add("-G", generator);

        if (!theInstallDir.isEmpty()) {
            args.addKeyValuePair("-D", "CMAKE_INSTALL_PREFIX", theInstallDir, false);
        }

        args.addKeyValuePair("-D", "CMAKE_BUILD_TYPE", theBuildType, false);

        if (!cmakeArgs.isEmpty()) args.addTokenized(cmakeArgs);

        args.add(theSourceDir);

        listener.getLogger().println(args.toString());

        int result = 0;
        try {
            result = launcher.launch()
                    .cmds(args)
                    .envs(envs)
                    .stdout(listener.getLogger())
                    .stderr(listener.getLogger())
                    .pwd(new FilePath(this.workSpace, this.buildDir))
                    .join();
        } catch (IOException e) {
            listener.getLogger().println(e.getMessage());
            return false;
        } catch (InterruptedException e) {
            listener.getLogger().println(e.getMessage());
            return false;
        }

        return result == 0;
    }

    public boolean launchMake(String makeCommand) {
        if (makeCommand.trim().isEmpty()) {
            this.listener.getLogger().println("No Make command, skipping...");
            return true;
        }

        ArgumentListBuilder args = new ArgumentListBuilder();
        args.addTokenized(makeCommand);

        listener.getLogger().println(args.toString());

        int result = 0;
        try {
            result = this.launcher.launch()
                    .cmds(args)
                    .envs(this.envs)
                    .stdout(this.listener.getLogger())
                    .stderr(this.listener.getLogger())
                    .pwd(new FilePath(this.workSpace, this.buildDir))
                    .join();
        } catch (IOException e) {
            listener.getLogger().println(e.getMessage());
            return false;
        } catch (InterruptedException e) {
            listener.getLogger().println(e.getMessage());
            return false;
        }

        return (result == 0);
    }

    public boolean launchInstall(String installDir, String installCommand) {
        if (installDir.isEmpty() || installCommand.trim().isEmpty()) {
            this.listener.getLogger().println("Skipping Make install...");
            return true;
        }

        ArgumentListBuilder args = new ArgumentListBuilder();
        args.addTokenized(installCommand);

        listener.getLogger().println(args.toString());

        int result = 0;
        try {
            result = this.launcher.launch()
                    .cmds(args)
                    .envs(this.envs)
                    .stdout(this.listener.getLogger())
                    .stderr(this.listener.getLogger())
                    .pwd(new FilePath(this.workSpace, this.buildDir))
                    .join();
        } catch (IOException e) {
            listener.getLogger().println(e.getMessage());
            return false;
        } catch (InterruptedException e) {
            listener.getLogger().println(e.getMessage());
            return false;
        }

        return (result == 0);
    }
}
