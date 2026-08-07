package net.minecraft.client.gui;

import net.minecraft.crash.CrashReport;

import java.awt.Desktop;
import java.io.File;
import java.util.Locale;

public class GuiCrashScreen extends GuiScreen {
    private final CrashReport report;

    public GuiCrashScreen(CrashReport report) {
        this.report = report;
    }

    @Override
    public void initGui() {
        this.mc.setIngameNotInFocus();
        this.buttonList.clear();
        int y = this.height / 4 + 132;
        this.buttonList.add(new GuiButton(0, this.width / 2 - 155, y, 150, 20, this.text("Back to title", "返回标题画面")));
        this.buttonList.add(new GuiButton(1, this.width / 2 + 5, y, 150, 20, this.text("Open report", "打开报告")));
        this.buttonList.add(new GuiButton(2, this.width / 2 - 75, y + 24, 150, 20, this.text("Copy report", "复制报告")));

        File reportFile = this.report.getFile();
        if (reportFile == null || !reportFile.isFile()) {
            this.buttonList.get(1).enabled = false;
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            this.mc.displayGuiScreen(new GuiMainMenu());
        } else if (button.id == 1) {
            this.openReport(button);
        } else if (button.id == 2) {
            setClipboardString(this.report.getCompleteReport());
            button.displayString = this.text("Copied", "已复制");
        }
    }

    private void openReport(GuiButton button) {
        try {
            File reportFile = this.report.getFile();
            if (reportFile == null || !reportFile.isFile()) {
                throw new IllegalStateException("Crash report file is missing");
            }
            Desktop.getDesktop().open(reportFile);
        } catch (Throwable throwable) {
            button.displayString = this.text("[Failed]", "[失败]");
            button.enabled = false;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        this.drawCenteredString(this.fontRendererObj, this.text("Minecraft crashed!", "Minecraft 崩溃了！"), this.width / 2, this.height / 4 - 40, 0xFFFFFF);

        int textColor = 0xD0D0D0;
        int x = this.width / 2 - 155;
        int y = this.height / 4;

        this.drawString(this.fontRendererObj, this.text("Minecraft ran into a problem and crashed.", "Minecraft 遇到问题并崩溃。"), x, y, textColor);
        this.drawString(this.fontRendererObj, this.text("The following module(s) have been identified as potential causes:", "以下模块可能导致了这次崩溃："), x, y += 18, textColor);
        this.drawCenteredString(this.fontRendererObj, this.text("Unknown", "未知"), this.width / 2, y += 11, 0xE0E000);
        this.drawString(this.fontRendererObj, this.text("A crash report has been generated and saved here:", "崩溃报告已生成并保存到："), x, y += 11, textColor);
        this.drawString(this.fontRendererObj, this.text("Click \"Open report\" to view it or \"Copy report\" to copy it.", "点击“打开报告”查看，或点击“复制报告”复制内容。"), x, y += 9, textColor);
        this.drawCenteredString(this.fontRendererObj, this.getReportName(), this.width / 2, y += 11, this.report.getFile() != null ? 0x00FF00 : 0xFF5555);
        this.drawString(this.fontRendererObj, this.text("You can return to the title screen and keep using the client.", "你可以返回标题画面并继续使用客户端。"), x, y += 12, textColor);
        this.drawString(this.fontRendererObj, this.text("If the crash happens again, send the copied report to whoever", "如果崩溃再次发生，请把复制的报告发送给"), x, y += 9, textColor);
        this.drawString(this.fontRendererObj, this.text("maintains the broken module or feature.", "对应模块或功能的维护者。"), x, y += 9, textColor);
        this.drawString(this.fontRendererObj, this.text("The current world/session was unloaded to recover safely.", "当前世界/会话已卸载，以便安全恢复。"), x, y + 9, textColor);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private String getReportName() {
        File reportFile = this.report.getFile();
        return reportFile != null ? "\u00A7n" + reportFile.getName() : this.text("[Error saving report, see log]", "[报告保存失败，请查看日志]");
    }

    private String text(String english, String chinese) {
        String language = this.mc != null && this.mc.gameSettings != null ? this.mc.gameSettings.language : null;
        return language != null && language.toLowerCase(Locale.ROOT).startsWith("zh") ? chinese : english;
    }
}
