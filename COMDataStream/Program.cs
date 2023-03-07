
using LiveChartsCore;
using LiveChartsCore.SkiaSharpView;

namespace COMDataStream
{
    internal static class Program
    {
        /// <summary>
        ///  The main entry point for the application.
        /// </summary>
        [STAThread]
        static void Main()
        {
            // To customize application configuration such as set high DPI settings or default font,
            // see https://aka.ms/applicationconfiguration.

            ApplicationConfiguration.Initialize();
            Application.Run(new Form1());

            //LiveCharts.Configure(config =>
            //    config
            //        // registers SkiaSharp as the library backend
            //        // REQUIRED unless you build your own
            //        .AddSkiaSharp()

            //        // adds the default supported types
            //        // OPTIONAL but highly recommend
            //        .AddDefaultMappers()

            //        // select a theme, default is Light
            //        // OPTIONAL
            //        .AddDarkTheme()
            //        //.AddLightTheme()
            //    );
        }
    }
}