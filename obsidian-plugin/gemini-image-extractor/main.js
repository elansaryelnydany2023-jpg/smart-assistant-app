const { Plugin, PluginSettingTab, Setting, Modal, Notice, TFolder, TFile, normalizePath } = require("obsidian");

const DEFAULT_SETTINGS = {
  apiKey: "",
  model: "gemini-2.0-flash",
  outputFolder: "Extraction",
  maxImages: 10,
  assistantPackage: "com.example.smartassistant"
};

class GeminiImageExtractorPlugin extends Plugin {
  async onload() {
    this.settings = Object.assign({}, DEFAULT_SETTINGS, await this.loadData());

    this.addRibbonIcon("image", "استخراج نص من الصور", () => {
      new FolderSelectModal(this).open();
    });

    this.addCommand({
      id: "extract-text-from-images",
      name: "استخراج نص من الصور",
      callback: () => new FolderSelectModal(this).open()
    });

    this.addCommand({
      id: "open-smart-assistant",
      name: "فتح المساعد الذكي",
      callback: () => this.openAssistant()
    });

    this.addSettingTab(new GeminiImageExtractorSettingTab(this.app, this));
  }

  onunload() {}

  async saveSettings() {
    await this.saveData(this.settings);
  }

  openAssistant() {
    const pkg = this.settings.assistantPackage;
    const url = `intent://open#Intent;scheme=smartassistant;package=${pkg};end`;
    try {
      window.location.href = url;
      new Notice("جارٍ فتح المساعد الذكي…");
    } catch (e) {
      new Notice("تعذر فتح المساعد الذكي. تأكد من تثبيت التطبيق.");
    }
  }

  async extractImages(folder, files) {
    if (!this.settings.apiKey) {
      new Notice("أدخل مفتاح Gemini من إعدادات الإضافة أولًا.");
      return;
    }

    const outputName = this.settings.outputFolder || "Extraction";
    const outputPath = normalizePath(`${folder.path}/${outputName}`);

    try {
      if (!this.app.vault.getAbstractFileByPath(outputPath)) {
        await this.app.vault.createFolder(outputPath);
      }
    } catch (e) {
      // المجلد موجود مسبقًا
    }

    new Notice(`بدأ استخراج النص من ${files.length} صور…`);

    const combined = [];
    let index = 0;

    for (const file of files) {
      index++;
      try {
        new Notice(`استخراج النص من الصورة ${index} من ${files.length}: ${file.name}`);

        const buffer = await this.app.vault.readBinary(file);
        const base64 = this.arrayBufferToBase64(buffer);

        const response = await fetch(
          `https://generativelanguage.googleapis.com/v1beta/models/${this.settings.model}:generateContent`,
          {
            method: "POST",
            headers: {
              "Content-Type": "application/json",
              "x-goog-api-key": this.settings.apiKey
            },
            body: JSON.stringify({
              contents: [{
                parts: [
                  { text: "استخرج جميع النصوص المرئية في الصورة بدقة. لا تلخص ولا تترجم ولا تضف معلومات غير موجودة. حافظ على لغة النص وترتيب الأسطر. إذا كان النص غير واضح اكتب: [غير واضح]." },
                  { inline_data: { mime_type: file.extension === "jpg" ? "image/jpeg" : `image/${file.extension}`, data: base64 } }
                ]
              }]
            })
          }
        );

        const data = await response.json();

        if (!response.ok) {
          throw new Error(data?.error?.message || `HTTP ${response.status}`);
        }

        const parts = data?.candidates?.[0]?.content?.parts || [];
        const text = parts.map(p => p.text || "").join("\n").trim();

        if (!text) throw new Error("لم يرجع Gemini نصًا.");

        const safeName = file.name.replace(/[\\/:*?"<>|]/g, "_").replace(/\.[^.]+$/, "");
        const fileName = `${String(index).padStart(2, "0")}_${safeName}.txt`;
        const filePath = normalizePath(`${outputPath}/${fileName}`);

        await this.app.vault.create(filePath, text);
        combined.push(`=== ${index}: ${file.name} ===\n${text}\n`);

      } catch (error) {
        new Notice(`فشل استخراج ${file.name}: ${error.message}`);
      }
    }

    if (combined.length) {
      const allPath = normalizePath(`${outputPath}/all_text.txt`);
      try {
        await this.app.vault.create(allPath, combined.join("\n"));
      } catch (e) {
        const existing = this.app.vault.getAbstractFileByPath(allPath);
        if (existing instanceof TFile) {
          await this.app.vault.modify(existing, combined.join("\n"));
        }
      }
      new Notice(`تم حفظ النصوص في: ${outputPath}`);
    }
  }

  arrayBufferToBase64(buffer) {
    const bytes = new Uint8Array(buffer);
    let binary = "";
    const chunk = 0x8000;

    for (let i = 0; i < bytes.length; i += chunk) {
      binary += String.fromCharCode.apply(null, bytes.subarray(i, i + chunk));
    }

    return btoa(binary);
  }
}

class FolderSelectModal extends Modal {
  constructor(plugin) {
    super(plugin.app);
    this.plugin = plugin;
  }

  onOpen() {
    this.titleEl.setText("اختر مجلد الصور");
    this.contentEl.createEl("p", { text: "اختر مجلدًا داخل الـVault يحتوي على الصور." });

    const folders = [];
    this.app.vault.getFiles().forEach(file => {
      const parent = file.parent;
      if (parent && !folders.some(f => f.path === parent.path)) folders.push(parent);
    });

    folders.sort((a, b) => a.path.localeCompare(b.path, "ar"));

    folders.forEach(folder => {
      const button = this.contentEl.createEl("button", { text: folder.path });
      button.className = "gemini-folder-button";
      button.onclick = () => {
        this.close();
        new ImageSelectModal(this.plugin, folder).open();
      };
    });
  }

  onClose() {}
}

class ImageSelectModal extends Modal {
  constructor(plugin, folder) {
    super(plugin.app);
    this.plugin = plugin;
    this.folder = folder;
    this.selected = [];
  }

  onOpen() {
    const { contentEl } = this;
    contentEl.empty();
    this.titleEl.setText(`اختر حتى ${this.plugin.settings.maxImages} صور`);

    const images = this.folder.children
      .filter(file => file instanceof TFile && ["jpg", "jpeg", "png", "webp"].includes(file.extension.toLowerCase()))
      .sort((a, b) => a.name.localeCompare(b.name, "ar"));

    if (!images.length) {
      contentEl.createEl("p", { text: "لا توجد صور مدعومة في هذا المجلد." });
      return;
    }

    const list = contentEl.createDiv("gemini-image-list");

    images.forEach(file => {
      const label = list.createEl("label", { cls: "gemini-image-item" });
      const checkbox = label.createEl("input");
      checkbox.type = "checkbox";

      label.createSpan({ text: ` ${file.name}` });

      checkbox.onchange = () => {
        if (checkbox.checked) {
          if (this.selected.length >= this.plugin.settings.maxImages) {
            checkbox.checked = false;
            new Notice(`الحد الأقصى ${this.plugin.settings.maxImages} صور.`);
            return;
          }
          this.selected.push(file);
        } else {
          this.selected = this.selected.filter(item => item !== file);
        }

        status.setText(`تم اختيار ${this.selected.length} صورة.`);
      };
    });

    const status = contentEl.createEl("p", { text: "تم اختيار 0 صورة." });

    const buttons = contentEl.createDiv("gemini-modal-buttons");

    const extractButton = buttons.createEl("button", { text: "استخراج وحفظ النصوص" });
    extractButton.className = "mod-cta";
    extractButton.onclick = () => {
      if (!this.selected.length) {
        new Notice("اختر صورة واحدة على الأقل.");
        return;
      }
      this.close();
      this.plugin.extractImages(this.folder, this.selected);
    };

    const assistantButton = buttons.createEl("button", { text: "فتح المساعد الذكي" });
    assistantButton.onclick = () => {
      this.close();
      this.plugin.openAssistant();
    };
  }

  onClose() {
    this.contentEl.empty();
  }
}

class GeminiImageExtractorSettingTab extends PluginSettingTab {
  constructor(app, plugin) {
    super(app, plugin);
    this.plugin = plugin;
  }

  display() {
    const { containerEl } = this;
    containerEl.empty();

    new Setting(containerEl)
      .setName("مفتاح Gemini API")
      .setDesc("أدخل مفتاح Gemini من Google AI Studio.")
      .addText(text => text
        .setPlaceholder("AIza...")
        .setValue(this.plugin.settings.apiKey)
        .onChange(async value => {
          this.plugin.settings.apiKey = value.trim();
          await this.plugin.saveSettings();
        }));

    new Setting(containerEl)
      .setName("اسم نموذج Gemini")
      .setDesc("استخدم اسم نموذج متاح في حسابك، مثل gemini-2.0-flash.")
      .addText(text => text
        .setValue(this.plugin.settings.model)
        .onChange(async value => {
          this.plugin.settings.model = value.trim();
          await this.plugin.saveSettings();
        }));

    new Setting(containerEl)
      .setName("مجلد حفظ النصوص")
      .setDesc("سيتم إنشاء هذا المجلد داخل مجلد الصور.")
      .addText(text => text
        .setValue(this.plugin.settings.outputFolder)
        .onChange(async value => {
          this.plugin.settings.outputFolder = value.trim() || "Extraction";
          await this.plugin.saveSettings();
        }));

    new Setting(containerEl)
      .setName("الحد الأقصى للصور")
      .setDesc("عدد الصور التي يمكن اختيارها في كل مرة.")
      .addText(text => text
        .setValue(String(this.plugin.settings.maxImages))
        .onChange(async value => {
          const number = parseInt(value, 10);
          this.plugin.settings.maxImages = Number.isFinite(number) && number > 0 ? number : 10;
          await this.plugin.saveSettings();
        }));

    new Setting(containerEl)
      .setName("ربط المساعد الذكي")
      .setDesc("اسم حزمة تطبيق المساعد الذكي على أندرويد.")
      .addText(text => text
        .setValue(this.plugin.settings.assistantPackage)
        .onChange(async value => {
          this.plugin.settings.assistantPackage = value.trim();
          await this.plugin.saveSettings();
        }))
      .addButton(button => button
        .setButtonText("فتح المساعد الذكي")
        .onClick(() => this.plugin.openAssistant()));
  }
}

module.exports = GeminiImageExtractorPlugin;
