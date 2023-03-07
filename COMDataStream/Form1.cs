using LiveChartsCore.SkiaSharpView;
using LiveChartsCore.SkiaSharpView.Painting;
using LiveChartsCore.SkiaSharpView.Painting.Effects;
using LiveChartsCore.SkiaSharpView.WinForms;
using SkiaSharp;
using System.IO.Ports;
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

        public Form1()
        {
            InitializeComponent();
            Size = new System.Drawing.Size(1200, 800);
            label5.Text = progressBar1.Value.ToString() + "%";

            _viewModel = new ViewModel();

            _cartesianChart = new CartesianChart
            {
                Series = _viewModel.Series,


                // out of livecharts properties...
                Location = new System.Drawing.Point(0, 100),
                Size = new System.Drawing.Size(1150, 650),
                Anchor = AnchorStyles.Left | AnchorStyles.Right | AnchorStyles.Top | AnchorStyles.Bottom
            };

            _cartesianChart.YAxes = new List<Axis> {
                new Axis
                {
                    Name = "Y Axis",
                    NamePaint = new SolidColorPaint(SKColors.GhostWhite),

                    LabelsPaint = new SolidColorPaint(SKColors.WhiteSmoke),
                    TextSize = 20,

                    SeparatorsPaint = new SolidColorPaint(SKColors.LightSlateGray)
                    {
                        StrokeThickness = 2,
                        PathEffect = new DashEffect(new float[] { 3, 3 })
                    }
                }
            };

            _cartesianChart.XAxes = new List<Axis> {
                new Axis
                {
                    Name = "X Axis",
                    NamePaint = new SolidColorPaint(SKColors.GhostWhite),

                    LabelsPaint = new SolidColorPaint(SKColors.WhiteSmoke),
                    TextSize = 20,

                    MaxLimit = 8000,
                    MinLimit = 20, // Set zoom when a COM port handshake is called.
                    MinStep = 5,

            SeparatorsPaint = new SolidColorPaint(SKColors.LightSlateGray)
                    {
                        StrokeThickness = 2,
                        PathEffect = new DashEffect(new float[] { 3, 3 })
                    }
                }
            };

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

        private void button1_Click(object sender, EventArgs e)
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

            switch (code)
            {
                case "C1": // Code C1: Pressure
                    // Parse hex string after C1 and convert to float
                    string hexNum = data.Substring(2,8);
                    //hexNum = ReverseString(hexNum);
                    var num = uint.Parse(hexNum, System.Globalization.NumberStyles.AllowHexSpecifier);
                    byte[] floatVals = BitConverter.GetBytes(num);
                    //floatVals = floatVals.Reverse().ToArray();
                    float f = BitConverter.ToSingle(floatVals, 0);
                    textBox1.Text = f.ToString();
                    // Add float value to datastream
                    graphPlotter(f);
                    break;
                case "C2":
                    break;
                case "C3":
                    break;
                default:
                    break;
            }
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
            }
            else
            {
                _viewModel.AddItem(f);
            }
            
        }


    }

}