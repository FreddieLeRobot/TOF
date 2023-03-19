

using System.Windows.Forms;

namespace COMDataStream
{
    partial class Form1
    {
        /// <summary>
        ///  Required designer variable.
        /// </summary>
        private System.ComponentModel.IContainer components = null;

        /// <summary>
        ///  Clean up any resources being used.
        /// </summary>
        /// <param name="disposing">true if managed resources should be disposed; otherwise, false.</param>
        protected override void Dispose(bool disposing)
        {
            if (disposing && (components != null))
            {
                components.Dispose();
            }
            base.Dispose(disposing);
        }

        #region Windows Form Designer generated code

        /// <summary>
        ///  Required method for Designer support - do not modify
        ///  the contents of this method with the code editor.
        /// </summary>
        private void InitializeComponent()
        {
            comboBox1 = new ComboBox();
            label1 = new Label();
            connButton = new Button();
            button2 = new Button();
            label2 = new Label();
            label3 = new Label();
            checkBox1 = new CheckBox();
            progressBar1 = new ProgressBar();
            label4 = new Label();
            label5 = new Label();
            textBox1 = new TextBox();
            button3 = new Button();
            checkBox2 = new CheckBox();
            label6 = new Label();
            checkBox3 = new CheckBox();
            saveFileDialog1 = new SaveFileDialog();
            label7 = new Label();
            textBox2 = new TextBox();
            button4 = new Button();
            SuspendLayout();
            // 
            // comboBox1
            // 
            comboBox1.FormattingEnabled = true;
            comboBox1.Location = new Point(12, 51);
            comboBox1.Name = "comboBox1";
            comboBox1.Size = new Size(121, 23);
            comboBox1.TabIndex = 0;
            comboBox1.DropDown += comboBox1_DropDownOpened;
            // 
            // label1
            // 
            label1.AutoSize = true;
            label1.Font = new Font("Segoe UI", 12F, FontStyle.Underline, GraphicsUnit.Point);
            label1.ForeColor = SystemColors.ButtonFace;
            label1.Location = new Point(12, 19);
            label1.Name = "label1";
            label1.Size = new Size(78, 21);
            label1.TabIndex = 1;
            label1.Text = "COM Port";
            // 
            // connButton
            // 
            connButton.Location = new Point(139, 51);
            connButton.Name = "connButton";
            connButton.Size = new Size(75, 23);
            connButton.TabIndex = 2;
            connButton.Text = "Connect";
            connButton.UseVisualStyleBackColor = true;
            connButton.Click += connButton_Click;
            // 
            // button2
            // 
            button2.BackColor = SystemColors.ButtonFace;
            button2.ForeColor = SystemColors.ActiveCaptionText;
            button2.Location = new Point(139, 20);
            button2.Name = "button2";
            button2.Size = new Size(75, 23);
            button2.TabIndex = 5;
            button2.Text = "Stop";
            button2.UseVisualStyleBackColor = false;
            button2.Click += button2_Click;
            // 
            // label2
            // 
            label2.AutoSize = true;
            label2.Font = new Font("Segoe UI", 9.75F, FontStyle.Regular, GraphicsUnit.Point);
            label2.ForeColor = SystemColors.ButtonFace;
            label2.Location = new Point(231, 23);
            label2.Name = "label2";
            label2.Size = new Size(38, 17);
            label2.TabIndex = 7;
            label2.Text = "Data:";
            // 
            // label3
            // 
            label3.AutoSize = true;
            label3.Font = new Font("Segoe UI", 12F, FontStyle.Regular, GraphicsUnit.Point);
            label3.ForeColor = SystemColors.ButtonFace;
            label3.Location = new Point(32, 92);
            label3.Name = "label3";
            label3.Size = new Size(73, 21);
            label3.TabIndex = 8;
            label3.Text = "Data Plot";
            // 
            // checkBox1
            // 
            checkBox1.AutoSize = true;
            checkBox1.Checked = true;
            checkBox1.CheckState = CheckState.Checked;
            checkBox1.ForeColor = SystemColors.ButtonFace;
            checkBox1.Location = new Point(234, 55);
            checkBox1.Name = "checkBox1";
            checkBox1.Size = new Size(109, 19);
            checkBox1.TabIndex = 9;
            checkBox1.Text = "Horizontal Sync";
            checkBox1.UseVisualStyleBackColor = true;
            checkBox1.CheckedChanged += checkBox1_CheckedChanged;
            // 
            // progressBar1
            // 
            progressBar1.Location = new Point(618, 55);
            progressBar1.Name = "progressBar1";
            progressBar1.Size = new Size(57, 16);
            progressBar1.TabIndex = 11;
            progressBar1.Value = 80;
            // 
            // label4
            // 
            label4.AutoSize = true;
            label4.ForeColor = SystemColors.ButtonFace;
            label4.Location = new Point(548, 55);
            label4.Name = "label4";
            label4.Size = new Size(69, 15);
            label4.TabIndex = 12;
            label4.Text = "Battery Life:";
            // 
            // label5
            // 
            label5.AutoSize = true;
            label5.ForeColor = SystemColors.ButtonFace;
            label5.Location = new Point(680, 55);
            label5.Name = "label5";
            label5.Size = new Size(23, 15);
            label5.TabIndex = 13;
            label5.Text = "0%";
            // 
            // textBox1
            // 
            textBox1.Location = new Point(274, 21);
            textBox1.Name = "textBox1";
            textBox1.Size = new Size(159, 23);
            textBox1.TabIndex = 6;
            // 
            // button3
            // 
            button3.Font = new Font("Segoe UI", 8.25F, FontStyle.Regular, GraphicsUnit.Point);
            button3.Location = new Point(349, 52);
            button3.Name = "button3";
            button3.Size = new Size(84, 23);
            button3.TabIndex = 14;
            button3.Text = "Reset Graph";
            button3.UseVisualStyleBackColor = true;
            button3.Click += button3_Click;
            // 
            // checkBox2
            // 
            checkBox2.AutoSize = true;
            checkBox2.ForeColor = Color.WhiteSmoke;
            checkBox2.Location = new Point(458, 24);
            checkBox2.Name = "checkBox2";
            checkBox2.Size = new Size(54, 19);
            checkBox2.TabIndex = 15;
            checkBox2.Text = "Sleep";
            checkBox2.UseVisualStyleBackColor = true;
            checkBox2.CheckedChanged += checkBox2_CheckedChanged;
            // 
            // label6
            // 
            label6.AutoSize = true;
            label6.ForeColor = SystemColors.ButtonFace;
            label6.Location = new Point(477, 55);
            label6.Name = "label6";
            label6.Size = new Size(56, 15);
            label6.TabIndex = 16;
            label6.Text = "Charging";
            // 
            // checkBox3
            // 
            checkBox3.AutoSize = true;
            checkBox3.ForeColor = SystemColors.ButtonFace;
            checkBox3.Location = new Point(762, 23);
            checkBox3.Name = "checkBox3";
            checkBox3.Size = new Size(97, 19);
            checkBox3.TabIndex = 17;
            checkBox3.Text = "Data Logging";
            checkBox3.UseVisualStyleBackColor = true;
            checkBox3.CheckedChanged += checkBox3_CheckedChanged;
            // 
            // saveFileDialog1
            // 
            saveFileDialog1.CreatePrompt = true;
            saveFileDialog1.Filter = "Text files (*.txt)|*.txt|Comma Seperated Value Files (*.csv)|*.csv|All files (*.*)|*.*";
            saveFileDialog1.OverwritePrompt = false;
            saveFileDialog1.FileOk += saveFileDialog1_FileOk;
            // 
            // label7
            // 
            label7.AutoSize = true;
            label7.ForeColor = SystemColors.ButtonFace;
            label7.Location = new Point(762, 55);
            label7.Name = "label7";
            label7.Size = new Size(52, 15);
            label7.TabIndex = 18;
            label7.Text = "Filepath:";
            // 
            // textBox2
            // 
            textBox2.Location = new Point(820, 48);
            textBox2.Name = "textBox2";
            textBox2.Size = new Size(280, 23);
            textBox2.TabIndex = 19;
            // 
            // button4
            // 
            button4.Location = new Point(1103, 48);
            button4.Name = "button4";
            button4.Size = new Size(24, 23);
            button4.TabIndex = 20;
            button4.Text = "...";
            button4.UseVisualStyleBackColor = true;
            button4.Click += button4_Click;
            // 
            // Form1
            // 
            AutoScaleDimensions = new SizeF(7F, 15F);
            AutoScaleMode = AutoScaleMode.Font;
            BackColor = Color.FromArgb(64, 64, 64);
            ClientSize = new Size(1184, 761);
            Controls.Add(button4);
            Controls.Add(textBox2);
            Controls.Add(label7);
            Controls.Add(checkBox3);
            Controls.Add(label6);
            Controls.Add(checkBox2);
            Controls.Add(button3);
            Controls.Add(label5);
            Controls.Add(label4);
            Controls.Add(progressBar1);
            Controls.Add(checkBox1);
            Controls.Add(label3);
            Controls.Add(label2);
            Controls.Add(textBox1);
            Controls.Add(button2);
            Controls.Add(connButton);
            Controls.Add(label1);
            Controls.Add(comboBox1);
            Name = "Form1";
            Text = "Form1";
            ResumeLayout(false);
            PerformLayout();
        }

        #endregion

        private ComboBox comboBox1;
        private Label label1;
        private Button button1;
        private Button button2;
        private Label label2;
        private Label label3;
        private CheckBox checkBox1;
        private ProgressBar progressBar1;
        private Label label4;
        private Label label5;
        private TextBox textBox1;
        private Button button3;
        private CheckBox checkBox2;
        private Label label6;
        private CheckBox checkBox3;
        private SaveFileDialog saveFileDialog1;
        private Label label7;
        private TextBox textBox2;
        private Button button4;
        private Button connButton;
    }
}