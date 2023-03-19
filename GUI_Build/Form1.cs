using LiveChartsCore;
using LiveChartsCore.SkiaSharpView;
using LiveChartsCore.SkiaSharpView.Painting;
using LiveChartsCore.SkiaSharpView.Painting.Effects;
using LiveChartsCore.SkiaSharpView.WinForms;
using SkiaSharp;
using System.IO.Ports;
using System.Reflection.Metadata;
using ViewModelsSamples.Lines.AutoUpdate;

namespace COMDataStream
{

    public partial class Form1 : Form
    {
        static SerialPort _serialPort;
        // From lvcharts2 autoupdate example:
        private readonly CartesianChart _cartesianChart;
        private readonly ViewModel _viewModel;
        private bool? _isStreaming = false;
        private bool _firstSleep = false;
        private bool _firstCharge = false;

        enum status
        {
            TIME,
            PRESSURE,
            SLEEP,
            BATT_V,
            BATT_P,
            BATT_C,
            ERROR
        };

        Object[] logStatus = new object[] { 0, 0.0, 0, 0.0, 0, 0, 0 }; //TIME, PRESSURE, SLEEP, BATT_V, BATT_P, BATT_C, ERROR

        public Form1()
        {
            InitializeComponent();
            Size = new System.Drawing.Size(1200, 800);
            label5.Text = progressBar1.Value.ToString() + "%";

            _viewModel = new ViewModel();

            _cartesianChart = new CartesianChart
            {
                Series = _viewModel.Series,
                XAxes = _viewModel.XAxes,
                EasingFunction = null,

                // out of livecharts properties...
                Location = new System.Drawing.Point(0, 100),
                Size = new System.Drawing.Size(1150, 650),
                Anchor = AnchorStyles.Left | AnchorStyles.Right | AnchorStyles.Top | AnchorStyles.Bottom
            };

            _cartesianChart.YAxes = new List<Axis> {
                new Axis
                {
                    Name = "Pressure",
                    NamePaint = new SolidColorPaint(SKColors.GhostWhite),

                    LabelsPaint = new SolidColorPaint(SKColors.WhiteSmoke),
                    TextSize = 15,

                    SeparatorsPaint = new SolidColorPaint(SKColors.LightSlateGray)
                    {
                        StrokeThickness = 2,
                        PathEffect = new DashEffect(new float[] { 3, 3 })
                    }
                }
            };

            // XAxis is fed datetime data, so it is it's own public Axis[] in Form 1_1

            _cartesianChart.DrawMarginFrame = new DrawMarginFrame
            {
                Stroke = new SolidColorPaint(SKColors.GhostWhite, 3)
            };

            // Single line properties
            //_cartesianChart.ZoomMode = LiveChartsCore.Measure.ZoomAndPanMode.X; Calling this when handshaking COM.
            _cartesianChart.ZoomingSpeed = 0.0001;


            Controls.Add(_cartesianChart);
            this.Paint += new PaintEventHandler(Form1_Paint);
        }

        void Form1_Paint(object sender, PaintEventArgs e)
        {
            Pen pen = new Pen(Color.SlateGray, 2);
            SolidBrush brush = new SolidBrush(Color.Red);

            e.Graphics.DrawEllipse(pen, 460, 57, 10, 10);
            e.Graphics.FillEllipse(brush, 460, 57, 10, 10);
        }



        private void comboBox1_DropDownOpened(object sender, EventArgs e)
        {
            string[] ports = SerialPort.GetPortNames();
            comboBox1.Items.Clear();
            foreach (string comport in ports)
            {
                comboBox1.Items.Add(comport);
            }
        }

        private void connButton_Click(object sender, EventArgs e)
        {
            string port = comboBox1.SelectedItem.ToString();
            textBox1.Text = "Connecting to " + port + "...";
            _serialPort = new SerialPort(port, 9600, Parity.None, 8, StopBits.One);
            _serialPort.Handshake = Handshake.None;
            _serialPort.DtrEnable = true;
            _serialPort.RtsEnable = true;
            _serialPort.DataReceived += new SerialDataReceivedEventHandler(sp_DataReceived);
            _serialPort.ReadTimeout = 1000;
            _serialPort.WriteTimeout = 1000;
            checkBox3.Enabled = false;
            connButton.Enabled = false;
            textBox2.Enabled = false;
            _isStreaming = true;

            //Thread.Sleep(1000);
            try
            {
                _serialPort.Open();
                //textBox1.Clear();
                _cartesianChart.ZoomMode = LiveChartsCore.Measure.ZoomAndPanMode.X;
            }
            catch (Exception exc3)
            {
                textBox1.Text = "Error!";
            }
        }

        private delegate void SetTextDeleg(string text);

        void sp_DataReceived(object sender, SerialDataReceivedEventArgs e)
        {
            var data = _serialPort.ReadLine();

            this.BeginInvoke(new SetTextDeleg(si_DataReceived), new object[] { data });
        }

        private void si_DataReceived(string data)
        {
            textBox1.Clear();
            textBox1.Text = data.Trim();
            postOffice(data); // Send to messahe handling function
            //_viewModel.AddItem(float.Parse(data));
        }

        private void button3_Click(object sender, EventArgs e)
        {
            _viewModel.RemoveSeries();
        }

        private void button2_Click(object sender, EventArgs e)
        {
            _serialPort.Close();
            checkBox3.Enabled = true;
            connButton.Enabled = true;
            textBox2.Enabled = true;
            _isStreaming = false;
        }

        private void checkBox1_CheckedChanged(object sender, EventArgs e)
        {
            if (checkBox1.Checked)
            {
                _cartesianChart.ZoomMode = 0;
            }
            else
            {
                _cartesianChart.ZoomMode = LiveChartsCore.Measure.ZoomAndPanMode.X;
            }
        }

        private void postOffice(string data)
        {
            string code = data.Substring(0, 2);
            float f = 0;

            switch (code)
            {
                case "C1": // Code C1: Pressure
                    // Parse hex string after C1 and convert to float
                    f = hexstringToFloat(data.Substring(2, 8));
                    graphPlotter(f);
                    break;
                case "C2": // C2: Charging/Battery Status (Low Charge, Charging0
                    switch (data.Substring(2, 2))
                    {
                        case "B1":
                            if (_firstCharge == false)
                            {
                                // TODO Change the charging indicator
                                _firstCharge = true;
                                logState(status.BATT_C, 1);

                            }
                            else
                            {
                                //textBox1.Text = "Charging";
                            }
                            break;
                        case "B0":
                            // TODO Change the charging indicator
                            _firstCharge = false;
                            logState(status.BATT_C, 0);
                            break;
                        case "B2":
                            // TODO Change the charging indicator
                            //_firstCharge = false;
                            logState(status.BATT_C, -1); // Low battery
                            break;
                    }
                    break;
                case "C3": // C3: Battery Voltage
                    f = hexstringToFloat(data.Substring(2, 8));
                    battPlotter(f);
                    break;
                case "C4": // C4: Sleep
                    switch (data.Substring(2, 2))
                    {
                        case "00":
                            if (_firstSleep == false)
                            {
                                checkBox2.Checked = true;
                                _firstSleep = true;
                                logState(status.SLEEP, 1);

                            }
                            else
                            {
                                textBox1.Text = "Sleep Mode";
                            }
                            break;
                        case "01":
                            checkBox2.Checked = false;
                            _firstSleep = false;
                            textBox1.Text = "Waking Up!";
                            logState(status.SLEEP, 0);
                            break;
                    }
                    break;
                case "C0": // C0: Error
                    switch (data.Substring(2, 2))
                    {
                        case "00":
                            logState(status.ERROR, 0); // No error
                            break;
                        case "01":
                            logState(status.ERROR, 1); // No error
                            textBox1.Text = "ERROR: INCORRECT COMMAND";
                            break;
                    }
                    break;
                default:
                    break;
            }
        }

        public float hexstringToFloat(string hexString)
        {
            float f;

            // Uncomment to reverse for little endian
            //hexNum = ReverseString(hexNum);

            var num = uint.Parse(hexString, System.Globalization.NumberStyles.AllowHexSpecifier);
            byte[] floatVals = BitConverter.GetBytes(num);

            f = BitConverter.ToSingle(floatVals, 0);
            //textBox1.Text = f.ToString();

            // Add float value to datastream
            return f;
        }

        public static string ReverseString(string str)
        {
            return String.IsNullOrEmpty(str) ? string.Empty : new string(str.ToCharArray().Reverse().ToArray());
        }

        private void graphPlotter(float f)
        {
            if (_viewModel.length() >= 8000)
            {
                _viewModel.scroll(f);
                logState(status.PRESSURE, f);
            }
            else
            {
                _viewModel.AddItem(f);
                logState(status.PRESSURE, f);
            }
            if (_isStreaming == true && checkBox3.Checked)
            {
                sendLog();
            }
        }

        private void battPlotter(float f)
        {
            // Get battery percentage based on read voltage.
            // 3.7V lipos start at 4.2V fully charged and is dead at 3.4.
            if (f < 3.4)
            {
                progressBar1.Value = 0;
                label5.Text = "0%";
                logState(status.BATT_P, 0);
            }
            else if (f > 4.2)
            {
                progressBar1.Value = 100;
                label5.Text = "100%";
                logState(status.BATT_P, 100);
            }
            else
            {
                float percentage = ((f - (float)3.4) / (float)0.8) * 100;
                textBox1.Text = percentage.ToString();
                progressBar1.Value = (int)Math.Round(percentage, 0);
                label5.Text = ((int)Math.Round(percentage, 0)).ToString() + "%";
                logState(status.BATT_P, (int)Math.Round(percentage, 0));
            }
            logState(status.BATT_V, f);
        }

        private void checkBox2_CheckedChanged(object sender, EventArgs e)
        {
            if (checkBox2.Checked)
            {
                try
                {
                    _serialPort.Write(new byte[] { 0xC4, 0x00 }, 0, 2);
                    //_serialPort.Write("\n");
                }
                catch
                {
                    textBox1.Text = "No Serial Port Connected!";
                    checkBox2.Checked = false;
                }
            }
            else
            {
                try
                {
                    _serialPort.Write(new byte[] { 0xC4, 0x01 }, 0, 2);
                    //_serialPort.Write("\n");
                }
                catch
                {
                    textBox1.Text = "No Serial Port Connected!";
                    checkBox2.Checked = false;
                }
            }
        }

        private void checkBox3_CheckedChanged(object sender, EventArgs e)
        {
            if (checkBox3.Checked)
            {
                if (textBox2.Text == "") saveFileDialog1.ShowDialog(this);
            }
        }

        private void saveFileDialog1_FileOk(object sender, System.ComponentModel.CancelEventArgs e)
        {
            textBox2.Text = saveFileDialog1.FileName;
            // If file doesn't exist, create file and add headers.
            if (!saveFileDialog1.CheckFileExists)
            {
                //File.Create(textBox2.Text);
                using StreamWriter file = new(textBox2.Text, append: true);
                file.Write("Time,Pressure,Sleeping,Battery Voltage,Battery %,Charging,ERROR");
                file.WriteLine();
            }
            else { }
        }


        private void button4_Click(object sender, EventArgs e)
        {
            saveFileDialog1.ShowDialog(this);
        }

        private void logState(status type, Object var)
        {
            switch (type)
            {
                case status.TIME:
                    logStatus[0] = var;
                    break;
                case status.PRESSURE:
                    logStatus[1] = var;
                    break;
                case status.SLEEP:
                    logStatus[2] = var;
                    break;
                case status.BATT_V:
                    logStatus[3] = var;
                    break;
                case status.BATT_P:
                    logStatus[4] = var;
                    break;
                case status.BATT_C:
                    logStatus[5] = var;
                    break;
                case status.ERROR:
                    logStatus[6] = var;
                    break;
                default:
                    break;

            }
        }

        private void sendLog()
        {
            DateTime current = DateTime.Now;
            logStatus[0] = current.ToString("yyyy-MM-dd HH:mm:ss.fff");
            using StreamWriter file = new(textBox2.Text, append: true);
            for (int i = 0; i < logStatus.Length; i++)
            {
                file.Write(logStatus[i].ToString());
                if (i < (logStatus.Length - 1))
                {
                    file.Write(',');
                }
                else if (i >= (logStatus.Length - 1))
                {
                    file.WriteLine();
                }
            }
        }
    }

}