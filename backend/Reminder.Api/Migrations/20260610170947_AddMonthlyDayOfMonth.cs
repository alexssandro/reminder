using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Reminder.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddMonthlyDayOfMonth : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<int>(
                name: "MonthlyDayOfMonth",
                table: "reminders",
                type: "integer",
                nullable: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "MonthlyDayOfMonth",
                table: "reminders");
        }
    }
}
