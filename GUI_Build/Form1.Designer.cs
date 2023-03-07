

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
            button1 = new Button();
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
            // button1
            // 
            button1.Location = new Point(139, 51);
            button1.Name = "button1";
            button1.Size = new Size(75, 23);
            button1.TabIndex = 2;
            button1.Text = "Connect";
            button1.UseVisualStyleBackColor = true;
            button1.Click += button1_Click;
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
            button3.Location = new Point(358, 51);
            button3.Name = "button3";
            button3.Size = new Size(75, 23);
            button3.TabIndex = 14;
            button3.Text = "Reset";
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
            // Form1
            // 
            AutoScaleDimensions = new SizeF(7F, 15F);
            AutoScaleMode = AutoScaleMode.Font;
            BackColor = Color.FromArgb(64, 64, 64);
            ClientSize = new Size(1184, 761);
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
            Controls.Add(button1);
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
    }
}